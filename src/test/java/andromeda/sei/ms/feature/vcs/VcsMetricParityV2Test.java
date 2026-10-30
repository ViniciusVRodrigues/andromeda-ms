package andromeda.sei.ms.feature.vcs;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetDeveloperDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetIndividualDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetOrganizationDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectOverviewV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.v2.ComplexityEffortFigures;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMethod;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.VcsReadService;
import andromeda.sei.ms.feature.vcs.application.usecase.CalculatePullRequestMetricsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * VCS1 - explicit parity of the VCS metrics of the four v2 dashboards (organization, project, team,
 * individual) against an independent oracle.
 *
 * <p>The oracle is written in this class from the fixture lists below with plain loops: it does not call
 * {@code ReviewMetricsPolicy}, {@code StalePullRequestPolicy}, {@code DeliveryMetricsCalculator},
 * {@code Percentiles} or {@code Ratios}. The only production code that runs before the dashboards is
 * {@code CalculatePullRequestMetricsUseCase}, which stores the per-PR cycle time and time to first review
 * the dashboards read (the same step the sync does); the oracle derives both again from the raw events.
 *
 * <p>Now = Thursday 2026-10-15 12:00Z. Current sprint s2 = 2026-10-08..2026-10-21, previous sprint s1 =
 * 2026-09-24..2026-10-07. The 30-day period is [2026-09-16, 2026-10-15], the previous one
 * [2026-08-17, 2026-09-15] (half-open instants: [from 00:00Z, last+1 00:00Z)).
 *
 * <p>The divergences VCS1 found were fixed in VCS2-VCS6 and their cases are active. Cases about behavior
 * that is deliberate are plain assertions of the current behavior with a comment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VcsMetricParityV2Test {

    private static final String ORG = "org-v2";
    private static final String PROJECT = "proj-v2";
    private static final String PROJECT_2 = "proj-v2b";
    private static final String REPO = "repo-v2";
    private static final String A = "user-ana";
    private static final String B = "user-bruno";
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-15");

    private static final Instant SPRINT_FROM = t("2026-10-08T00:00:00");
    private static final Instant SPRINT_TO = t("2026-10-22T00:00:00");
    private static final Instant PREV_SPRINT_FROM = t("2026-09-24T00:00:00");
    private static final Instant PREV_SPRINT_TO = t("2026-10-08T00:00:00");
    private static final Instant PERIOD_FROM = t("2026-09-16T00:00:00");
    private static final Instant PERIOD_TO = t("2026-10-16T00:00:00");
    /** Time series start on the first Monday inside the period (the leading partial week is dropped by design). */
    private static final Instant BUCKETS_FROM = t("2026-09-21T00:00:00");
    private static final Instant PREV_PERIOD_FROM = t("2026-08-17T00:00:00");
    private static final Instant PREV_PERIOD_TO = t("2026-09-16T00:00:00");

    private static final Sprint S1 = sprint("s1", "Sprint 1", Sprint.TimeFrame.PAST, "2026-09-24", "2026-10-07");
    private static final Sprint S2 = sprint("s2", "Sprint 2", Sprint.TimeFrame.CURRENT, "2026-10-08", "2026-10-21");

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private GetDeveloperDashboardUseCase developerDashboard;
    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private AzureProjectBindingRepository azureBindingRepository;
    @Mock private IntegrationSyncLogRepository syncLogRepository;

    private final InMemoryVcsStore store = new InMemoryVcsStore();
    private final List<Pr> prs = new ArrayList<>();
    private final List<Ev> evs = new ArrayList<>();
    private final List<Cm> cms = new ArrayList<>();
    /** Branches of the binding of {@link #PROJECT_2}; null = no second binding. */
    private List<String> secondBindingBranches;
    /** Branches of the binding of {@link #PROJECT} (production branch main); null = default (main only). */
    private List<String> firstBindingBranches;

    private VcsReadService read;
    private DeliveryMetricsService delivery;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        buildBaseFixture();

        read = new VcsReadService(store.bindingRepository, store.repositoryRepository, store.pullRequestRepository,
                store.reviewEventRepository);
        delivery = new DeliveryMetricsService(read, workItemRepository, organizationRepository);

        Project project = new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of(A, B), 0L, NOW, NOW);
        Project project2 = new Project(PROJECT_2, ORG, "Bootes", "bootes", null, true, List.of(A), 0L, NOW, NOW);
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(project));
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT_2, ORG)).thenReturn(Optional.of(project2));
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project, project2));
        User ana = user(A, "Ana Ribeiro", "gh-ana");
        User bruno = user(B, "Bruno Lima", "gh-bruno");
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(ana, bruno));
        when(userRepository.findByIdAndOrganizationId(A, ORG)).thenReturn(Optional.of(ana));
        when(userRepository.findByIdAndOrganizationId(B, ORG)).thenReturn(Optional.of(bruno));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(S1, S2));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s2", ORG)).thenReturn(Optional.of(S2));
        when(organizationRepository.findById(anyString())).thenReturn(Optional.empty());
        when(developerDashboard.execute(eq(ORG), anyString(), eq(PROJECT), eq("s2"), any(), any(), any()))
                .thenAnswer(inv -> devOutput());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /**
     * One repository, one project, two members (A and B) and a ghost author with no linked user.
     * Every figure the tests assert is derived from these lists by the oracle, not typed in.
     */
    private void buildBaseFixture() {
        // p1: A, merged into main in 24h. Self comment, a deleted and an inactive event come before the
        // first real review (B at 12:00, 3h after creation).
        prs.add(new Pr("p1", A, PullRequestStatus.MERGED, "2026-10-08T09:00:00").merged("2026-10-09T09:00:00")
                .linked("wi-1"));
        evs.add(new Ev("e1", "p1", B, ReviewKind.COMMENTED, "2026-10-08T12:00:00"));
        evs.add(new Ev("e2", "p1", B, ReviewKind.APPROVED, "2026-10-09T08:00:00"));
        evs.add(new Ev("e3", "p1", A, ReviewKind.COMMENTED, "2026-10-08T10:00:00"));
        evs.add(new Ev("e8", "p1", B, ReviewKind.COMMENTED, "2026-10-08T11:00:00").deleted());
        evs.add(new Ev("e9", "p1", B, ReviewKind.COMMENTED, "2026-10-08T10:30:00").inactive());
        // p2: A, merged into main in 96h; the bot comment does not count; earliest commit before creation.
        prs.add(new Pr("p2", A, PullRequestStatus.MERGED, "2026-10-09T10:00:00").merged("2026-10-13T10:00:00")
                .earliest("2026-10-08T20:00:00"));
        evs.add(new Ev("e4", "p2", B, ReviewKind.APPROVED, "2026-10-12T10:00:00"));
        evs.add(new Ev("e5", "p2", null, ReviewKind.COMMENTED, "2026-10-09T10:30:00").bot());
        // p3: B, merged into main in 12h, a hotfix (remediation).
        prs.add(new Pr("p3", B, PullRequestStatus.MERGED, "2026-10-10T08:00:00").merged("2026-10-10T20:00:00")
                .source("hotfix/p3"));
        evs.add(new Ev("e6", "p3", A, ReviewKind.COMMENTED, "2026-10-10T09:00:00"));
        // p4: B, open for 74h with no review: stale.
        prs.add(new Pr("p4", B, PullRequestStatus.OPEN, "2026-10-12T10:00:00").linked("wi-4"));
        // p6: unlinked author, merged into main in 72h.
        prs.add(new Pr("p6", null, PullRequestStatus.MERGED, "2026-10-11T08:00:00").merged("2026-10-14T08:00:00"));
        evs.add(new Ev("e7", "p6", A, ReviewKind.CHANGES_REQUESTED, "2026-10-11T20:00:00"));
        // p7: A, merged into "release" (not the production branch) in 10h, never reviewed.
        prs.add(new Pr("p7", A, PullRequestStatus.MERGED, "2026-10-12T08:00:00").merged("2026-10-12T18:00:00")
                .base("release").source("feature/p7"));
        // p8: A, closed without merge after 28h, never reviewed.
        prs.add(new Pr("p8", A, PullRequestStatus.CLOSED, "2026-10-09T08:00:00").closed("2026-10-10T12:00:00"));
        // p12: A, a closed draft: out of every review figure, still listed for A.
        prs.add(new Pr("p12", A, PullRequestStatus.CLOSED, "2026-10-09T09:00:00").closed("2026-10-12T09:00:00")
                .draft());
        // p10: A, merged into main on 09-21 (inside the 30 days, before the sprint).
        prs.add(new Pr("p10", A, PullRequestStatus.MERGED, "2026-09-20T08:00:00").merged("2026-09-21T08:00:00"));
        evs.add(new Ev("e10", "p10", B, ReviewKind.APPROVED, "2026-09-20T20:00:00"));
        // p11: B, merged into main on 08-22 (previous 30-day period).
        prs.add(new Pr("p11", B, PullRequestStatus.MERGED, "2026-08-20T08:00:00").merged("2026-08-22T08:00:00"));

        cms.add(new Cm("c1", A, "2026-10-08T10:00:00"));
        cms.add(new Cm("c2", A, "2026-10-08T15:00:00"));
        cms.add(new Cm("c3", A, "2026-10-09T10:00:00"));
        cms.add(new Cm("c4", A, "2026-10-14T23:59:59"));
        cms.add(new Cm("c5", B, "2026-10-10T10:00:00"));
        cms.add(new Cm("c6", null, "2026-10-11T10:00:00"));
        cms.add(new Cm("c7", B, "2026-10-08T00:00:00"));              // first instant of the sprint
        cms.add(new Cm("c8", A, "2026-10-07T23:59:59"));              // last second before the sprint
        cms.add(new Cm("c9", A, "2026-10-09T11:00:00").inactive());   // inactive: never counts
        cms.add(new Cm("c10", A, "2026-09-20T10:00:00"));
        cms.add(new Cm("c11", A, "2026-09-25T10:00:00"));
        cms.add(new Cm("c12", A, "2026-10-16T00:00:00"));             // first instant after the period
        cms.add(new Cm("c13", B, "2026-08-25T10:00:00"));             // previous period
    }

    /** Writes the fixture lists into the in-memory store and runs the per-PR metrics step of the sync. */
    private void load() {
        store.repositories.clear();
        store.bindings.clear();
        store.pullRequests.clear();
        store.commits.clear();
        store.events.clear();

        VcsRepository repo = new VcsRepository(REPO, ORG, VcsProvider.GITHUB, "ext-repo", "owner/orion");
        repo.setDefaultBranch("main");
        store.repositories.add(repo);
        VcsRepoBinding first = new VcsRepoBinding("bind-1", ORG, PROJECT, REPO, VcsProvider.GITHUB, true);
        if (firstBindingBranches != null) {
            first.setBranches(firstBindingBranches);
            first.setProductionBranch("main");
        }
        store.bindings.add(first);
        if (secondBindingBranches != null) {
            VcsRepoBinding second = new VcsRepoBinding("bind-2", ORG, PROJECT_2, REPO, VcsProvider.GITHUB, true);
            second.setBranches(secondBindingBranches);
            second.setProductionBranch("main");
            store.bindings.add(second);
        }
        int number = 1;
        for (Pr p : prs) {
            VcsPullRequest pr = new VcsPullRequest(p.id, ORG, REPO, VcsProvider.GITHUB, "ext-" + p.id, number++,
                    p.title, p.status);
            pr.setAuthorUserId(p.author);
            pr.setAuthorLogin(p.login);
            pr.setCreatedAt(p.created);
            pr.setMergedAt(p.merged);
            pr.setClosedAt(p.closed);
            pr.setBaseBranch(p.base);
            pr.setSourceBranch(p.source);
            pr.setDraft(p.draft);
            pr.setEarliestCommitAt(p.earliest);
            pr.setLinkedWorkItemExternalId(p.workItem);
            store.pullRequests.add(pr);
        }
        for (Ev e : evs) {
            VcsReviewEvent event = new VcsReviewEvent(e.id, ORG, e.pr, REPO, VcsProvider.GITHUB, "ext-" + e.id, e.kind,
                    e.at);
            event.setActorUserId(e.actor);
            event.setActorLogin(e.actor == null ? "ci-bot" : e.actor);
            event.setBot(e.bot);
            event.setDeleted(e.deleted);
            event.setActive(e.active);
            store.events.add(event);
        }
        for (Cm c : cms) {
            VcsCommit commit = new VcsCommit(c.id, ORG, REPO, VcsProvider.GITHUB, "sha-" + c.id);
            commit.setAuthorUserId(c.author);
            commit.setAuthoredAt(c.at);
            commit.setActive(c.active);
            store.commits.add(commit);
        }
        new CalculatePullRequestMetricsUseCase(store.pullRequestRepository, store.reviewEventRepository)
                .execute(ORG, REPO);
    }

    // ------------------------------------------------------------------
    // Use case factories (package-private Clock constructors, hence reflection)
    // ------------------------------------------------------------------

    private GetProjectOverviewV2UseCase projectUseCase() {
        return create(GetProjectOverviewV2UseCase.class, projectRepository, workItemRepository, sprintRepository,
                new ProjectVcsActivity(read, store.commitRepository), read, new EffortBearingLevelResolver(),
                new ProjectAiCostCalculator(allocationRepository, aiUsageRepository), aiUsageRepository,
                allocationRepository, delivery, peopleDirectory(), organizationRepository, clock());
    }

    private GetTeamDashboardV2UseCase teamUseCase() {
        return create(GetTeamDashboardV2UseCase.class, projectRepository, peopleDirectory(),
                new DashboardSprintResolver(sprintRepository), new ProjectVcsActivity(read, store.commitRepository),
                workItemRepository, new EffortBearingLevelResolver(),
                new ProjectAiCostCalculator(allocationRepository, aiUsageRepository), aiUsageRepository,
                allocationRepository, new ListProjectRepositoriesUseCase(read, delivery), clock());
    }

    private GetIndividualDashboardV2UseCase individualUseCase() {
        return create(GetIndividualDashboardV2UseCase.class, projectRepository, peopleDirectory(), developerDashboard,
                new DashboardSprintResolver(sprintRepository), new ProjectVcsActivity(read, store.commitRepository),
                workItemRepository, new EffortBearingLevelResolver(), aiUsageRepository, allocationRepository, clock());
    }

    private GetOrganizationDashboardV2UseCase organizationUseCase() {
        OrganizationDashboardData data = new OrganizationDashboardData(projectRepository, userRepository,
                workItemRepository, sprintRepository, new EffortBearingLevelResolver(), store.bindingRepository,
                store.repositoryRepository, read, store.commitRepository, delivery, organizationRepository,
                aiUsageRepository, allocationRepository, connectorTokenRepository, azureConnectionRepository,
                githubConnectionRepository, azureBindingRepository, syncLogRepository);
        return create(GetOrganizationDashboardV2UseCase.class, data, clock());
    }

    private DashboardPeopleDirectory peopleDirectory() {
        return new DashboardPeopleDirectory(userRepository, connectorTokenRepository);
    }

    private static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @SuppressWarnings("unchecked")
    private static <T> T create(Class<T> type, Object... args) {
        try {
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                Class<?>[] parameters = c.getParameterTypes();
                if (parameters.length == args.length && parameters[parameters.length - 1] == Clock.class) {
                    c.setAccessible(true);
                    return (T) c.newInstance(args);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("no constructor with a Clock in " + type);
    }

    // ------------------------------------------------------------------
    // Project dashboard
    // ------------------------------------------------------------------

    @Test
    void should_matchOracle_prsMergedAndPrsCreated_onProjectDashboard_v2() {
        load();

        ProjectOverviewV2Output out = projectUseCase().execute(ORG, PROJECT, 30);

        assertThat(out.kpis().prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
        assertThat(out.previous().prsMerged()).isEqualTo(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO).size());
        assertThat(out.prFlow().stream().mapToInt(ProjectOverviewV2Output.PrFlowRow::opened).sum())
                .as("prsCreated over the buckets").isEqualTo(created(BUCKETS_FROM, PERIOD_TO).size());
        assertThat(out.prFlow().stream().mapToInt(ProjectOverviewV2Output.PrFlowRow::merged).sum())
                .as("prsMerged over the buckets").isEqualTo(merged(BUCKETS_FROM, PERIOD_TO).size());
        assertThat(out.repos()).singleElement().satisfies(row ->
                assertThat(row.prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size()));
    }

    @Test
    void should_matchOracle_openAndStale_onProjectDashboard_v2() {
        load();

        ProjectOverviewV2Output out = projectUseCase().execute(ORG, PROJECT, 30);

        assertThat(out.kpis().stalePrs()).isEqualTo(stale(Set.of("main")).size());
        assertThat(out.previous().stalePrs()).as("stale is a state of now: no previous value").isNull();
        assertThat(out.repos()).singleElement().satisfies(row -> {
            assertThat(row.openPrs()).isEqualTo(open().size());
            assertThat(row.stalePrs()).isEqualTo(stale(Set.of("main")).size());
        });
    }

    @Test
    void should_matchOracle_timeToFirstReview_andCycle_onProjectDashboard_v2() {
        load();

        ProjectOverviewV2Output out = projectUseCase().execute(ORG, PROJECT, 30);

        assertThat(out.kpis().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(PERIOD_FROM, PERIOD_TO))));
        assertThat(out.previous().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(PREV_PERIOD_FROM, PREV_PERIOD_TO))));
        assertThat(out.kpis().prCycleTimeHours()).isEqualTo(stat(cycleOf(merged(PERIOD_FROM, PERIOD_TO))));
        assertThat(out.previous().prCycleTimeHours()).isEqualTo(stat(cycleOf(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO))));
        assertThat(out.repos()).singleElement().satisfies(row -> {
            assertThat(row.prCycleTimeHours()).isEqualTo(stat(cycleOf(merged(PERIOD_FROM, PERIOD_TO))));
            assertThat(row.timeToFirstReviewHours()).isEqualTo(stat(ttfrOf(created(PERIOD_FROM, PERIOD_TO))));
        });
    }

    @Test
    void should_matchOracle_commits_onProjectDashboard_v2() {
        load();

        ProjectOverviewV2Output out = projectUseCase().execute(ORG, PROJECT, 30);

        assertThat(out.kpis().commitsCount()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true));
        assertThat(out.previous().commitsCount()).isEqualTo(commits(PREV_PERIOD_FROM, PREV_PERIOD_TO, c -> true));
        assertThat(out.repos()).singleElement().satisfies(row ->
                assertThat(row.commits()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true)));
    }

    @Test
    void should_matchOracle_delivery_onProjectDashboard_v2() {
        load();

        ProjectOverviewV2Output out = projectUseCase().execute(ORG, PROJECT, 30);

        Oracle current = deliveryOf(PERIOD_FROM, PERIOD_TO, "main");
        Oracle previous = deliveryOf(PREV_PERIOD_FROM, PREV_PERIOD_TO, "main");
        assertSoftly(s -> {
            s.assertThat(out.dora().method()).isEqualTo(DeliveryMethod.VCS_PROXY.name());
            s.assertThat(out.dora().deployFrequency().perWeek()).isEqualTo(current.deploysPerWeek());
            s.assertThat(out.dora().changeFailureRate().pct()).isEqualTo(current.cfr());
            s.assertThat(out.dora().leadTime().hours()).isEqualTo(current.leadTime());
            s.assertThat(out.kpis().deployFreqPerWeek()).isEqualTo(current.deploysPerWeek());
            s.assertThat(out.kpis().cfrPct()).isEqualTo(current.cfr());
            s.assertThat(out.previous().deployFreqPerWeek()).isEqualTo(previous.deploysPerWeek());
            s.assertThat(out.previous().cfrPct()).isEqualTo(previous.cfr());
            s.assertThat(out.repos().getFirst().cfrPct()).isEqualTo(current.cfr());
        });
        assertThat(current.merges()).as("the oracle sees merges into the production branch only").isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // Team dashboard
    // ------------------------------------------------------------------

    @Test
    void should_matchOracle_prsMergedAndPrsCreated_onTeamDashboard_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        assertThat(out.kpis().prsMerged()).isEqualTo(merged(SPRINT_FROM, SPRINT_TO).size());
        assertThat(out.previous().prsMerged()).isEqualTo(merged(PREV_SPRINT_FROM, PREV_SPRINT_TO).size());
        // team member rows: opened = created in the window, merged = merged in it, per author
        for (String member : List.of(A, B)) {
            TeamDashboardV2Output.Member row = member(out, member);
            assertThat(row.prsOpened()).as("opened by " + member)
                    .isEqualTo(created(SPRINT_FROM, SPRINT_TO).stream().filter(p -> member.equals(p.author)).count());
            assertThat(row.prsMerged()).as("merged by " + member)
                    .isEqualTo(merged(SPRINT_FROM, SPRINT_TO).stream().filter(p -> member.equals(p.author)).count());
        }
        assertThat(out.repos()).singleElement().satisfies(row ->
                assertThat(row.prsMerged()).isEqualTo(merged(SPRINT_FROM, SPRINT_TO).size()));
    }

    @Test
    void should_matchOracle_openAndStale_onTeamDashboard_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        assertThat(out.kpis().openPrs()).isEqualTo(open().size());
        assertThat(out.kpis().stalePrs()).isEqualTo(stale(Set.of("main")).size());
        assertThat(out.stalePrs()).extracting(v -> v.id())
                .containsExactlyElementsOf(stale(Set.of("main")).stream()
                        .sorted(Comparator.comparing((Pr p) -> p.created)).map(p -> p.id).toList());
    }

    @Test
    void should_matchOracle_timeToFirstReview_onTeamDashboard_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        assertThat(out.kpis().timeToFirstReviewHours()).isEqualTo(stat(ttfrOf(created(SPRINT_FROM, SPRINT_TO))));
        assertThat(out.previous().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(PREV_SPRINT_FROM, PREV_SPRINT_TO))));
        // The SLA is read by the repository card with the same fixture (every unreviewed PR is past 24h
        // or already closed, so it is the same at the fixed clock and at the real one).
        assertThat(out.repos()).singleElement().satisfies(row -> {
            assertThat(row.timeToFirstReviewHours()).isEqualTo(stat(ttfrOf(created(SPRINT_FROM, SPRINT_TO))));
            assertThat(row.reviewedWithinSlaPercent()).isEqualTo(slaPercent(created(SPRINT_FROM, SPRINT_TO), NOW));
        });
    }

    @Test
    void should_matchOracle_commits_onTeamDashboard_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        assertThat(out.commitsByDay()).extracting(CommitDay::date).containsExactlyElementsOf(
                days(LocalDate.parse("2026-10-08"), TODAY).stream().map(LocalDate::toString).toList());
        assertThat(commitsByDay(out.commitsByDay())).isEqualTo(commitsPerDay(LocalDate.parse("2026-10-08"), TODAY, c -> true));
        assertThat(out.repos()).singleElement().satisfies(row ->
                assertThat(row.commits()).isEqualTo(commits(SPRINT_FROM, SPRINT_TO, c -> true)));
    }

    @Test
    void should_matchOracle_teamMemberRows_onTeamDashboard_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        // B reviewed other people's PRs only: the rule and the counter agree for B (the self-review case,
        // which is A's, is the divergence 1 test below).
        assertThat(member(out, B).reviewsGiven()).isEqualTo(reviewsGiven(B, SPRINT_FROM, SPRINT_TO).size());
        assertThat(member(out, A).isMe()).isTrue();
        assertThat(member(out, B).isMe()).isFalse();
        assertThat(out.members()).extracting(TeamDashboardV2Output.Member::userId).containsExactlyInAnyOrder(A, B);
    }

    // ------------------------------------------------------------------
    // Individual dashboard
    // ------------------------------------------------------------------

    @Test
    void should_matchOracle_prStats_onIndividualDashboard_v2() {
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, A, PROJECT, "s2");

        List<Pr> mine = byAuthor(A);
        assertThat(out.kpis().prsMerged()).isEqualTo(merged(mine, SPRINT_FROM, SPRINT_TO).size());
        assertThat(out.kpis().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(mine, SPRINT_FROM, SPRINT_TO))));
        assertThat(out.previous().prsMerged()).isEqualTo(merged(mine, PREV_SPRINT_FROM, PREV_SPRINT_TO).size());
        assertThat(out.previous().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(mine, PREV_SPRINT_FROM, PREV_SPRINT_TO))));
    }

    @Test
    void should_matchOracle_pullRequestsList_onIndividualDashboard_v2() {
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, A, PROJECT, "s2");

        // The person's PRs touching the sprint: created, merged or closed in it, or still open (current
        // sprint). Open first, then newest created.
        List<String> expected = byAuthor(A).stream()
                .filter(p -> in(p.created, SPRINT_FROM, SPRINT_TO) || in(p.merged, SPRINT_FROM, SPRINT_TO)
                        || in(p.closed, SPRINT_FROM, SPRINT_TO) || p.status == PullRequestStatus.OPEN)
                .sorted(Comparator.comparing((Pr p) -> p.status == PullRequestStatus.OPEN ? 0 : 1)
                        .thenComparing((Pr p) -> p.created, Comparator.reverseOrder()))
                .map(p -> p.id).toList();
        assertThat(out.pullRequests()).extracting(v -> v.id()).containsExactlyElementsOf(expected);
        assertThat(expected).contains("p8", "p12");
    }

    @Test
    void should_matchOracle_openAndStale_onIndividualDashboard_v2() {
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, B, PROJECT, "s2");

        // B has p4 (open, stale) and no draft in the base fixture.
        assertThat(out.kpis().prsOpen()).isEqualTo(open(B).size());
        assertThat(out.pullRequests().stream().filter(v -> v.stale()).map(v -> v.id()).toList())
                .containsExactlyElementsOf(stale(Set.of("main")).stream().filter(p -> B.equals(p.author))
                        .map(p -> p.id).toList());
    }

    @Test
    void should_matchOracle_commitsByDay_onIndividualDashboard_v2() {
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, A, PROJECT, "s2");

        assertThat(commitsByDay(out.commitsByDay()))
                .isEqualTo(commitsPerDay(LocalDate.parse("2026-10-08"), TODAY, c -> A.equals(c.author)));
    }

    @Test
    void should_matchOracle_reviewsGiven_ofSomeoneWithoutSelfReviews_onIndividualDashboard_v2() {
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, B, PROJECT, "s2");

        List<Ev> given = reviewsGiven(B, SPRINT_FROM, SPRINT_TO);
        assertThat(out.reviewsGiven().total()).isEqualTo(given.size());
        assertThat(out.reviewsGiven().approved()).isEqualTo(count(given, ReviewKind.APPROVED));
        assertThat(out.reviewsGiven().commented()).isEqualTo(count(given, ReviewKind.COMMENTED));
    }

    // ------------------------------------------------------------------
    // Organization dashboard
    // ------------------------------------------------------------------

    @Test
    void should_matchOracle_prsMergedAndPrsCreated_onOrganizationDashboard_v2() {
        load();

        OrganizationDashboardV2Output out = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertThat(out.kpis().prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
        assertThat(out.previous().prsMerged()).isEqualTo(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO).size());
        assertThat(out.engineering().activity().stream().mapToInt(OrganizationDashboardV2Output.ActivityRow::opened).sum())
                .isEqualTo(created(BUCKETS_FROM, PERIOD_TO).size());
        assertThat(out.engineering().activity().stream().mapToInt(OrganizationDashboardV2Output.ActivityRow::merged).sum())
                .isEqualTo(merged(BUCKETS_FROM, PERIOD_TO).size());
    }

    @Test
    void should_matchOracle_openAndStale_onOrganizationDashboard_v2() {
        load();

        OrganizationDashboardV2Output out = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertThat(out.portfolio()).singleElement().satisfies(row ->
                assertThat(row.stalePrs()).isEqualTo(stale(Set.of("main")).size()));
        assertThat(out.engineering().repos()).singleElement().satisfies(row -> {
            assertThat(row.openPrs()).isEqualTo(open().size());
            assertThat(row.stalePrs()).isEqualTo(stale(Set.of("main")).size());
        });
    }

    @Test
    void should_matchOracle_timeToFirstReview_andCycle_onOrganizationDashboard_v2() {
        load();

        OrganizationDashboardV2Output out = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertThat(out.kpis().timeToFirstReviewHours()).isEqualTo(stat(ttfrOf(created(PERIOD_FROM, PERIOD_TO))));
        assertThat(out.previous().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(PREV_PERIOD_FROM, PREV_PERIOD_TO))));
        assertThat(out.portfolio().getFirst().prCycleTimeHours()).isEqualTo(stat(cycleOf(merged(PERIOD_FROM, PERIOD_TO))));
        assertThat(out.engineering().repos().getFirst().prCycleTimeHours())
                .isEqualTo(stat(cycleOf(merged(PERIOD_FROM, PERIOD_TO))));
    }

    @Test
    void should_matchOracle_commits_onOrganizationDashboard_v2() {
        load();

        OrganizationDashboardV2Output out = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertThat(out.kpis().commits()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true));
        assertThat(out.previous().commits()).isEqualTo(commits(PREV_PERIOD_FROM, PREV_PERIOD_TO, c -> true));
        assertThat(out.engineering().activity().stream().mapToInt(OrganizationDashboardV2Output.ActivityRow::commits).sum())
                .isEqualTo(commits(BUCKETS_FROM, PERIOD_TO, c -> true));
    }

    @Test
    void should_matchOracle_delivery_onOrganizationDashboard_v2() {
        load();

        OrganizationDashboardV2Output out = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        Oracle current = deliveryOf(PERIOD_FROM, PERIOD_TO, "main");
        Oracle previous = deliveryOf(PREV_PERIOD_FROM, PREV_PERIOD_TO, "main");
        assertSoftly(s -> {
            s.assertThat(out.kpis().cfrPct()).isEqualTo(current.cfr());
            s.assertThat(out.previous().cfrPct()).isEqualTo(previous.cfr());
            s.assertThat(out.portfolio().getFirst().cfrPct()).isEqualTo(current.cfr());
            s.assertThat(out.engineering().repos().getFirst().cfrPct()).isEqualTo(current.cfr());
        });
    }

    @Test
    void should_matchOracle_prLinkedPct_onOrganizationDashboard_v2() {
        load();

        OrganizationDashboardV2Output out = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        List<Pr> createdInPeriod = created(PERIOD_FROM, PERIOD_TO);
        long linked = createdInPeriod.stream().filter(p -> p.workItem != null).count();
        double expected = Math.round(100.0 * linked / createdInPeriod.size() * 100) / 100.0;
        assertThat(out.integrations().coverage().prsLinkedPct()).isEqualTo(expected);
        assertThat(linked).as("fixture links p1 and p4").isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Cross-dashboard consistency (same period)
    // ------------------------------------------------------------------

    @Test
    void should_agreeOnTheSamePeriod_acrossProjectAndOrganization_v2() {
        load();

        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertSoftly(s -> {
            s.assertThat(org.kpis().prsMerged()).isEqualTo(project.kpis().prsMerged());
            s.assertThat(org.kpis().commits()).isEqualTo(project.kpis().commitsCount());
            s.assertThat(org.kpis().cfrPct()).isEqualTo(project.kpis().cfrPct());
            s.assertThat(org.kpis().timeToFirstReviewHours()).isEqualTo(project.kpis().timeToFirstReviewHours());
            s.assertThat(org.portfolio().getFirst().stalePrs()).isEqualTo(project.kpis().stalePrs());
            s.assertThat(org.portfolio().getFirst().prCycleTimeHours()).isEqualTo(project.kpis().prCycleTimeHours());
            s.assertThat(org.previous().prsMerged()).isEqualTo(project.previous().prsMerged());
            s.assertThat(org.previous().commits()).isEqualTo(project.previous().commitsCount());
        });
    }

    @Test
    void should_agreeOnTheSameSprint_acrossTeamAndIndividual_v2() {
        load();

        TeamDashboardV2Output team = teamUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output ana = individualUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output bruno = individualUseCase().execute(ORG, B, PROJECT, "s2");

        // team prsMerged = oracle; the person's own merged count = the member row of the team
        assertThat(team.kpis().prsMerged()).isEqualTo(merged(SPRINT_FROM, SPRINT_TO).size());
        assertThat(ana.kpis().prsMerged()).isEqualTo(member(team, A).prsMerged());
        assertThat(bruno.kpis().prsMerged()).isEqualTo(member(team, B).prsMerged());
        // The ttfr of the team is the one of every PR created in the window; the person's is their own.
        assertThat(team.kpis().stalePrs()).isEqualTo(stale(Set.of("main")).size());
        assertThat(ana.kpis().timeToFirstReviewHours())
                .isEqualTo(stat(ttfrOf(created(byAuthor(A), SPRINT_FROM, SPRINT_TO))));
    }

    @Test
    void should_sumProjectCommitsPerDay_andKeepUnlinkedOnlyInTheTeamTotal_v2() {
        load();

        TeamDashboardV2Output team = teamUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output ana = individualUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output bruno = individualUseCase().execute(ORG, B, PROJECT, "s2");

        int teamTotal = team.commitsByDay().stream().mapToInt(CommitDay::commits).sum();
        int anaTotal = ana.commitsByDay().stream().mapToInt(CommitDay::commits).sum();
        int brunoTotal = bruno.commitsByDay().stream().mapToInt(CommitDay::commits).sum();
        long unlinked = cms.stream().filter(c -> c.active && c.author == null)
                .filter(c -> in(c.at, SPRINT_FROM, t("2026-10-16T00:00:00"))).count();
        // The team total includes the commit with no linked author; each individual only their own.
        assertThat(unlinked).isEqualTo(1);
        assertThat(teamTotal).isEqualTo(anaTotal + brunoTotal + (int) unlinked);
        assertThat(teamTotal).isEqualTo(commits(SPRINT_FROM, t("2026-10-16T00:00:00"), c -> true));

        // Project commits of the period = sum of the commits of every day of the period.
        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        int perDay = commitsPerDay(LocalDate.parse("2026-09-16"), TODAY, c -> true).values().stream()
                .mapToInt(Integer::intValue).sum();
        assertThat(project.kpis().commitsCount()).isEqualTo(perDay);
    }

    // ------------------------------------------------------------------
    // Map divergences (1, 2, 4, 6, 8) and by-design cases (3, 5, 7)
    // ------------------------------------------------------------------

    /**
     * Divergence 1 (team member row): {@code reviewsGiven} counts every review event of the person in
     * the window, including the comments on their own pull requests; {@code ReviewMetricsPolicy} excludes
     * the author's own events from the review figures.
     * Fixture: e3 is A commenting on A's own PR p1. Reference: A gave 2 reviews (e6 on p3, e7 on p6);
     * dashboard: 3. Cause: GetTeamDashboardV2UseCase.java:303 (the filter is only actorUserId).
     */
    @Test
    void should_notCountSelfReviews_inTeamMemberReviewsGiven_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        assertThat(member(out, A).reviewsGiven()).isEqualTo(reviewsGiven(A, SPRINT_FROM, SPRINT_TO).size());
    }

    /**
     * Divergence 1 (individual): same cause, {@code reviewsGiven()} of the Individual dashboard
     * (GetIndividualDashboardV2UseCase.java:286-292) counts e3, the self comment on p1.
     */
    @Test
    void should_notCountSelfReviews_inIndividualReviewsGiven_v2() {
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, A, PROJECT, "s2");

        List<Ev> given = reviewsGiven(A, SPRINT_FROM, SPRINT_TO);
        assertSoftly(s -> {
            s.assertThat(out.reviewsGiven().total()).isEqualTo(given.size());
            s.assertThat(out.reviewsGiven().commented()).isEqualTo(count(given, ReviewKind.COMMENTED));
            s.assertThat(out.reviewsGiven().changesRequested()).isEqualTo(count(given, ReviewKind.CHANGES_REQUESTED));
        });
    }

    /**
     * Divergence 2: a PR with {@code mergedAt} set but status CLOSED. The dashboards count merged by
     * {@code mergedAt} only; the delivery port used by the repository card ({@code mergedChanges}) and
     * the Mongo query behind it require status MERGED. The reference counts a merge only when both say so.
     * Fixture: p9, A, status CLOSED, mergedAt 2026-10-11. Reference: period prsMerged = 6 (sprint 5), dashboards 7 (6).
     * Cause: the mergedAt-only filters of GetProjectOverviewV2UseCase.java:205 (also
     * and Team/Individual through ProjectVcsActivity), OrganizationDashboardData.java:310 and
     * DeliveryMetricsService.java:79).
     */
    @Test
    void should_notCountAClosedPullRequestWithMergedAt_asMerged_v2() {
        prs.add(new Pr("p9", A, PullRequestStatus.CLOSED, "2026-10-09T08:00:00").merged("2026-10-11T08:00:00")
                .closed("2026-10-11T08:00:00"));
        load();

        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        TeamDashboardV2Output team = teamUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output ana = individualUseCase().execute(ORG, A, PROJECT, "s2");
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertSoftly(s -> {
            s.assertThat(project.kpis().prsMerged()).as("project").isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
            s.assertThat(org.kpis().prsMerged()).as("organization").isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
            s.assertThat(team.kpis().prsMerged()).as("team").isEqualTo(merged(SPRINT_FROM, SPRINT_TO).size());
            s.assertThat(ana.kpis().prsMerged()).as("individual")
                    .isEqualTo(merged(byAuthor(A), SPRINT_FROM, SPRINT_TO).size());
        });
    }

    /**
     * Divergence 3 (by design): {@code prsMerged} counts a merge into any branch (p7 went into
     * "release"); deploys per week and the failure rate only see merges into the production branch.
     * So prsMerged (6) is larger than the merges behind deploys/week (5). This is the intended rule.
     */
    @Test
    void should_countMergesIntoAnyBranch_butDeployOnlyTheProductionBranch_v2() {
        load();

        ProjectOverviewV2Output out = projectUseCase().execute(ORG, PROJECT, 30);

        assertThat(out.kpis().prsMerged()).isEqualTo(6);
        assertThat(deliveryOf(PERIOD_FROM, PERIOD_TO, "main").merges()).isEqualTo(5);
        assertThat(out.kpis().deployFreqPerWeek()).isEqualTo(deliveryOf(PERIOD_FROM, PERIOD_TO, "main").deploysPerWeek());
    }

    /**
     * Divergence 4: a repository bound to two projects with different branches. The reference reads the
     * branches of each project's own binding: for PROJECT_2 (branches main + release) the open PR
     * release -> main is a promotion, not stale. The organization dashboard builds one branch set per
     * repository from the first binding it reads (putIfAbsent), so PROJECT_2's row uses PROJECT's
     * binding (branches: main) and counts the promotion as stale.
     * Fixture: p30 (open, base main, source release, created 10-10), bind-1 (PROJECT, default branch)
     * read first, bind-2 (PROJECT_2, main + release). Reference: portfolio stalePrs of PROJECT_2 = 1 (p4,
     * as the project dashboard of PROJECT_2 says); dashboard: 2.
     * Cause: StalePullRequestPolicy.java:49 (putIfAbsent), used by OrganizationDashboardData (chosenBranches).
     */
    @Test
    void should_useTheBranchesOfEachProjectBinding_inTheOrganizationPortfolio_v2() {
        secondBindingBranches = List.of("main", "release");
        prs.add(new Pr("p30", A, PullRequestStatus.OPEN, "2026-10-10T08:00:00").base("main").source("release"));
        load();

        ProjectOverviewV2Output project2 = projectUseCase().execute(ORG, PROJECT_2, 30);
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT, PROJECT_2), 30);

        int expectedForProject2 = stale(Set.of("main", "release")).size();
        OrganizationDashboardV2Output.PortfolioRow row = org.portfolio().stream()
                .filter(r -> PROJECT_2.equals(r.projectId())).findFirst().orElseThrow();
        assertSoftly(s -> {
            s.assertThat(project2.kpis().stalePrs()).as("project dashboard of PROJECT_2").isEqualTo(expectedForProject2);
            s.assertThat(row.stalePrs()).as("organization portfolio row of PROJECT_2").isEqualTo(expectedForProject2);
        });
    }

    /**
     * Divergence 5 (by design): the team flow queue sees only the PRs created inside the sprint. Not
     * covered by a number here: the queue is not one of the VCS figures compared with the other dashboards
     * (see the report of VCS1/1).
     */

    /**
     * Divergence 6: {@code ListProjectRepositoriesUseCase} judges "stale" with {@code Instant.now()} while
     * the team dashboard uses its own clock. A PR open for 23h at the dashboard's now is not stale for
     * the KPI; the repository card, running later, counts it. The reference is one instant for both.
     * Fixture: p20 open, created 2026-10-14T13:00 (23h before NOW). Reference: card stale = KPI stale = 1
     * (p4); card: 2. Cause: ListProjectRepositoriesUseCase.java:53 (Instant.now()) read by
     * GetTeamDashboardV2UseCase.java:329-346.
     */
    @Test
    void should_useTheSameInstant_inTheTeamKpiAndTheRepositoryCard_v2() {
        prs.add(new Pr("p20", B, PullRequestStatus.OPEN, "2026-10-14T13:00:00"));
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        int expected = stale(Set.of("main")).size();
        assertSoftly(s -> {
            s.assertThat(out.kpis().stalePrs()).as("KPI").isEqualTo(expected);
            s.assertThat(out.repos().getFirst().stalePrs()).as("repository card").isEqualTo(expected);
        });
    }

    /**
     * Divergence 7 (by design): the sum of the member rows is not the team total, because a PR (and a
     * commit) whose author is not linked to a project member counts in the total and in no row. Here p6
     * has no linked author: the team merged 5 PRs, the members 4.
     */
    @Test
    void should_leaveUnlinkedAuthorsOutOfTheMemberRows_butInTheTeamTotal_v2() {
        load();

        TeamDashboardV2Output out = teamUseCase().execute(ORG, A, PROJECT, "s2");

        int rows = out.members().stream().mapToInt(m -> m.prsMerged() == null ? 0 : m.prsMerged()).sum();
        long unlinkedMerged = merged(SPRINT_FROM, SPRINT_TO).stream().filter(p -> p.author == null).count();
        assertThat(unlinkedMerged).isEqualTo(1);
        assertThat(rows).isEqualTo(out.kpis().prsMerged() - (int) unlinkedMerged);
    }

    /**
     * Divergence 8: "open" in the Individual KPI counts drafts. The reference counts a PR as open for
     * the person when it is open and ready for review (not a draft), the same notion as stale and the
     * review SLA. Note the other dashboards count {@code status == OPEN} too (drafts included), so the
     * question is one of definition, not of Individual alone.
     * Fixture: p5, B, OPEN draft. Reference: B has 1 open PR (p4); dashboard: 2.
     * Cause: GetIndividualDashboardV2UseCase.java:188.
     */
    @Test
    void should_notCountDrafts_inIndividualOpenPullRequests_v2() {
        prs.add(new Pr("p5", B, PullRequestStatus.OPEN, "2026-10-11T10:00:00").draft());
        load();

        IndividualDashboardV2Output out = individualUseCase().execute(ORG, B, PROJECT, "s2");

        assertThat(out.kpis().prsOpen()).isEqualTo(open(B).stream().filter(p -> !p.draft).count());
    }

    // ------------------------------------------------------------------
    // VCS1/2: time zone and window boundaries (BLOQUEIO 1)
    // ------------------------------------------------------------------

    /**
     * O dia é o dia UTC; não há fuso da organização. Commits escritos com offset -03:00 que mudam de dia em
     * UTC: 2026-10-14T22:00-03:00 = 10-15T01:00Z (A) e 2026-10-14T23:30-03:00 = 10-15T02:30Z (B).
     * 2026-10-15T22:00-03:00 = 10-16T01:00Z é do dia 16 em UTC: fora do período e do gráfico.
     */
    @Test
    void should_putItemsOnTheUtcDay_notTheLocalDay_inCommitsByDay_andCounts_v2() {
        cms.add(new Cm("tz1", A, "2026-10-14T22:00:00-03:00"));
        cms.add(new Cm("tz2", B, "2026-10-14T23:30:00-03:00"));
        cms.add(new Cm("tz3", A, "2026-10-15T22:00:00-03:00"));
        load();

        TeamDashboardV2Output team = teamUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output ana = individualUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output bruno = individualUseCase().execute(ORG, B, PROJECT, "s2");
        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        LocalDate d14 = LocalDate.parse("2026-10-14");
        LocalDate d15 = LocalDate.parse("2026-10-15");
        Map<LocalDate, Integer> teamDays = commitsByDay(team.commitsByDay());
        assertSoftly(s -> {
            s.assertThat(teamDays).isEqualTo(commitsPerDay(LocalDate.parse("2026-10-08"), TODAY, c -> true));
            s.assertThat(commitsByDay(ana.commitsByDay()))
                    .isEqualTo(commitsPerDay(LocalDate.parse("2026-10-08"), TODAY, c -> A.equals(c.author)));
            s.assertThat(commitsByDay(bruno.commitsByDay()))
                    .isEqualTo(commitsPerDay(LocalDate.parse("2026-10-08"), TODAY, c -> B.equals(c.author)));
            // small literals: the local day 14 would hold tz1 and tz2; the UTC day is 15 (c4 is the only one on 14)
            s.assertThat(teamDays.get(d14)).as("team UTC day 14").isEqualTo(1);
            s.assertThat(teamDays.get(d15)).as("team UTC day 15").isEqualTo(2);
            s.assertThat(commitsByDay(ana.commitsByDay()).get(d15)).as("A UTC day 15").isEqualTo(1);
            s.assertThat(commitsByDay(bruno.commitsByDay()).get(d15)).as("B UTC day 15").isEqualTo(1);
            // tz3 (local 10-15, UTC 10-16) is outside the 30 days and outside the chart
            s.assertThat(project.kpis().commitsCount()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true));
            s.assertThat(org.kpis().commits()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true));
            s.assertThat(project.kpis().commitsCount()).as("10 in the base period + tz1 + tz2, tz3 out").isEqualTo(12);
        });
    }

    /**
     * O dia é o dia UTC; não há fuso da organização. PRs com offset -03:00: criado 10-07T20:00-03:00
     * (= 10-07T23:00Z) e mergeado 10-07T22:30-03:00 (= 10-08T01:30Z) cai na sprint s2 (começa 10-08Z), embora
     * seja dia 7 no horário local. Outro, mergeado 09-15T23:00-03:00 (= 09-16T02:00Z), entra no período de 30
     * dias (que começa 09-16Z) e não no anterior, embora seja dia 15 no horário local.
     */
    @Test
    void should_countPullRequestsByTheUtcInstant_acrossSprintAndPeriodStarts_v2() {
        prs.add(new Pr("tzp1", A, PullRequestStatus.MERGED, "2026-10-07T20:00:00-03:00")
                .merged("2026-10-07T22:30:00-03:00"));
        prs.add(new Pr("tzp2", B, PullRequestStatus.MERGED, "2026-09-15T22:00:00-03:00")
                .merged("2026-09-15T23:00:00-03:00"));
        load();

        TeamDashboardV2Output team = teamUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output ana = individualUseCase().execute(ORG, A, PROJECT, "s2");
        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        assertSoftly(s -> {
            s.assertThat(team.kpis().prsMerged()).isEqualTo(merged(SPRINT_FROM, SPRINT_TO).size());
            s.assertThat(team.previous().prsMerged()).isEqualTo(merged(PREV_SPRINT_FROM, PREV_SPRINT_TO).size());
            s.assertThat(ana.kpis().prsMerged()).isEqualTo(merged(byAuthor(A), SPRINT_FROM, SPRINT_TO).size());
            s.assertThat(project.kpis().prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
            s.assertThat(project.previous().prsMerged()).isEqualTo(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO).size());
            s.assertThat(org.kpis().prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
            s.assertThat(org.previous().prsMerged()).isEqualTo(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO).size());
            // literals: sprint 5 + tzp1 = 6; period 6 + tzp1 + tzp2 = 8; previous period stays 1 (p11)
            s.assertThat(team.kpis().prsMerged()).isEqualTo(6);
            s.assertThat(project.kpis().prsMerged()).isEqualTo(8);
            s.assertThat(project.previous().prsMerged()).isEqualTo(1);
        });
    }

    /**
     * Fronteira do fim da sprint, com a sprint anterior s1 (09-24..10-07), que já terminou no Clock 10-15
     * (nada fica no futuro): o último segundo (10-07T23:59:59Z) é da s1; 10-08T00:00:00Z já é da s2. Vale para
     * PR mergeado e para commit (c8 e c7 da fixture-base estão nesses dois instantes).
     */
    @Test
    void should_keepTheLastSecondOfASprintInIt_andTheNextInstantInTheNext_v2() {
        prs.add(new Pr("pb1", A, PullRequestStatus.MERGED, "2026-10-07T10:00:00").merged("2026-10-07T23:59:59"));
        prs.add(new Pr("pb2", A, PullRequestStatus.MERGED, "2026-10-07T11:00:00").merged("2026-10-08T00:00:00"));
        load();
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s1", ORG)).thenReturn(Optional.of(S1));

        TeamDashboardV2Output s1 = teamUseCase().execute(ORG, A, PROJECT, "s1");
        TeamDashboardV2Output s2 = teamUseCase().execute(ORG, A, PROJECT, "s2");

        LocalDate d7 = LocalDate.parse("2026-10-07");
        LocalDate d8 = LocalDate.parse("2026-10-08");
        Map<LocalDate, Integer> s1Days = commitsByDay(s1.commitsByDay());
        assertSoftly(s -> {
            s.assertThat(s1.kpis().prsMerged()).isEqualTo(merged(PREV_SPRINT_FROM, PREV_SPRINT_TO).size());
            s.assertThat(s2.kpis().prsMerged()).isEqualTo(merged(SPRINT_FROM, SPRINT_TO).size());
            s.assertThat(s2.previous().prsMerged()).isEqualTo(merged(PREV_SPRINT_FROM, PREV_SPRINT_TO).size());
            s.assertThat(s1Days).isEqualTo(commitsPerDay(LocalDate.parse("2026-09-24"), d7, c -> true));
            s.assertThat(s1Days).as("s1 chart ends on its last day").doesNotContainKey(d8);
            // literals: pb1 is the only merge of s1; pb2 joins the 5 of s2
            s.assertThat(s1.kpis().prsMerged()).isEqualTo(1);
            s.assertThat(s2.kpis().prsMerged()).isEqualTo(6);
            s.assertThat(s1Days.get(d7)).as("c8 at 23:59:59Z is in s1").isEqualTo(1);
            s.assertThat(commitsByDay(s2.commitsByDay()).get(d8)).as("c1, c2 and c7 (00:00:00Z) are in s2").isEqualTo(3);
        });
    }

    /**
     * Fronteira do período de 30 dias [09-16 00:00Z, 10-16 00:00Z). Começo (coerente com o Clock): merge e
     * commit em 09-15T23:59:59Z são do período anterior, em 09-16T00:00:00Z do atual. Fim: 10-15T23:59:59Z
     * entra e 10-16T00:00:00Z não (esses dois instantes ficam depois do Clock 10-15T12:00Z; o teste é só da janela).
     */
    @Test
    void should_honorTheHalfOpenWindowOfThePeriod_atBothEnds_v2() {
        prs.add(new Pr("pw1", A, PullRequestStatus.MERGED, "2026-09-15T10:00:00").merged("2026-09-15T23:59:59"));
        prs.add(new Pr("pw2", A, PullRequestStatus.MERGED, "2026-09-15T11:00:00").merged("2026-09-16T00:00:00"));
        prs.add(new Pr("pw3", B, PullRequestStatus.MERGED, "2026-10-15T09:00:00").merged("2026-10-15T23:59:59"));
        prs.add(new Pr("pw4", B, PullRequestStatus.MERGED, "2026-10-15T10:00:00").merged("2026-10-16T00:00:00"));
        cms.add(new Cm("cw1", A, "2026-09-15T23:59:59"));
        cms.add(new Cm("cw2", A, "2026-09-16T00:00:00"));
        cms.add(new Cm("cw3", B, "2026-10-15T23:59:59"));
        load();

        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        Oracle current = deliveryOf(PERIOD_FROM, PERIOD_TO, "main");
        Oracle previous = deliveryOf(PREV_PERIOD_FROM, PREV_PERIOD_TO, "main");
        assertSoftly(s -> {
            s.assertThat(project.kpis().prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
            s.assertThat(project.previous().prsMerged()).isEqualTo(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO).size());
            s.assertThat(org.kpis().prsMerged()).isEqualTo(merged(PERIOD_FROM, PERIOD_TO).size());
            s.assertThat(org.previous().prsMerged()).isEqualTo(merged(PREV_PERIOD_FROM, PREV_PERIOD_TO).size());
            s.assertThat(project.kpis().commitsCount()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true));
            s.assertThat(project.previous().commitsCount()).isEqualTo(commits(PREV_PERIOD_FROM, PREV_PERIOD_TO, c -> true));
            s.assertThat(org.kpis().commits()).isEqualTo(commits(PERIOD_FROM, PERIOD_TO, c -> true));
            s.assertThat(org.previous().commits()).isEqualTo(commits(PREV_PERIOD_FROM, PREV_PERIOD_TO, c -> true));
            s.assertThat(project.kpis().deployFreqPerWeek()).isEqualTo(current.deploysPerWeek());
            s.assertThat(project.previous().deployFreqPerWeek()).isEqualTo(previous.deploysPerWeek());
            s.assertThat(project.kpis().cfrPct()).isEqualTo(current.cfr());
            // literals: period 6 + pw2 + pw3 = 8 (pw4 out); previous 1 (p11) + pw1 = 2
            s.assertThat(project.kpis().prsMerged()).isEqualTo(8);
            s.assertThat(project.previous().prsMerged()).isEqualTo(2);
            s.assertThat(project.kpis().commitsCount()).as("10 in the base period + cw2 + cw3").isEqualTo(12);
            s.assertThat(project.previous().commitsCount()).as("c13 + cw1").isEqualTo(2);
        });
    }

    // ------------------------------------------------------------------
    // VCS1/2: BRN1, one binding with branches [main, release] (BLOQUEIO 2)
    // ------------------------------------------------------------------

    /**
     * BRN1: um vínculo com branches [main, release] e produção main. r1 é um PR aberto antigo release -> main
     * sem revisão: promoção, NÃO é stale. p4 e r2 (feature -> main, sem revisão, antigos) são stale. Nos quatro
     * dashboards o stale bate com o oráculo; deploys e cfr contam só merges em main (o merge de p7 em release
     * fica fora). Sem a promoção seriam 3 stale.
     */
    @Test
    void should_exemptPromotionFromStale_andDeployOnlyOnProduction_withOneBindingOfTwoBranches_v2() {
        firstBindingBranches = List.of("main", "release");
        prs.add(new Pr("r1", A, PullRequestStatus.OPEN, "2026-10-10T08:00:00").base("main").source("release"));
        prs.add(new Pr("r2", B, PullRequestStatus.OPEN, "2026-10-11T08:00:00").base("main").source("feature/r2"));
        load();

        Set<String> chosen = Set.of("main", "release");
        List<String> staleIds = stale(chosen).stream().map(p -> p.id).toList();
        ProjectOverviewV2Output project = projectUseCase().execute(ORG, PROJECT, 30);
        TeamDashboardV2Output team = teamUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output ana = individualUseCase().execute(ORG, A, PROJECT, "s2");
        IndividualDashboardV2Output bruno = individualUseCase().execute(ORG, B, PROJECT, "s2");
        OrganizationDashboardV2Output org = organizationUseCase().execute(ORG, Set.of(PROJECT), 30);

        Oracle current = deliveryOf(PERIOD_FROM, PERIOD_TO, "main");
        assertSoftly(s -> {
            s.assertThat(staleIds).as("oracle: p4 and r2 are stale, r1 is a promotion").containsExactlyInAnyOrder("p4", "r2");
            s.assertThat(project.kpis().stalePrs()).as("project").isEqualTo(staleIds.size());
            s.assertThat(project.repos().getFirst().stalePrs()).as("project repo row").isEqualTo(staleIds.size());
            s.assertThat(team.kpis().stalePrs()).as("team kpi").isEqualTo(staleIds.size());
            s.assertThat(team.stalePrs()).extracting(v -> v.id())
                    .containsExactlyElementsOf(stale(chosen).stream()
                            .sorted(Comparator.comparing((Pr p) -> p.created)).map(p -> p.id).toList());
            s.assertThat(ana.pullRequests().stream().filter(v -> v.stale()).map(v -> v.id()).toList())
                    .as("individual A").containsExactlyElementsOf(
                            stale(chosen).stream().filter(p -> A.equals(p.author)).map(p -> p.id).toList());
            s.assertThat(bruno.pullRequests().stream().filter(v -> v.stale()).map(v -> v.id()).toList())
                    .as("individual B").containsExactlyInAnyOrderElementsOf(
                            stale(chosen).stream().filter(p -> B.equals(p.author)).map(p -> p.id).toList());
            s.assertThat(org.portfolio().getFirst().stalePrs()).as("organization portfolio").isEqualTo(staleIds.size());
            s.assertThat(org.engineering().repos().getFirst().stalePrs()).as("organization repo").isEqualTo(staleIds.size());
            // deploys / cfr: production branch main only (p7 merged into release is out)
            s.assertThat(current.merges()).isEqualTo(5);
            s.assertThat(project.kpis().deployFreqPerWeek()).isEqualTo(current.deploysPerWeek());
            s.assertThat(project.kpis().cfrPct()).isEqualTo(current.cfr());
            s.assertThat(org.kpis().cfrPct()).isEqualTo(current.cfr());
            s.assertThat(project.kpis().prsMerged()).as("prsMerged still sees the release merge").isEqualTo(6);
            s.assertThat(staleIds).hasSize(2);
        });
    }

    // ------------------------------------------------------------------
    // Oracle: plain loops over the fixture lists
    // ------------------------------------------------------------------

    private List<Pr> created(Instant from, Instant to) {
        return created(prs, from, to);
    }

    private static List<Pr> created(List<Pr> list, Instant from, Instant to) {
        List<Pr> out = new ArrayList<>();
        for (Pr p : list) if (in(p.created, from, to)) out.add(p);
        return out;
    }

    private List<Pr> merged(Instant from, Instant to) {
        return merged(prs, from, to);
    }

    /** Merged = status MERGED and a merge instant inside [from, to). */
    private static List<Pr> merged(List<Pr> list, Instant from, Instant to) {
        List<Pr> out = new ArrayList<>();
        for (Pr p : list) if (p.status == PullRequestStatus.MERGED && in(p.merged, from, to)) out.add(p);
        return out;
    }

    private List<Pr> byAuthor(String author) {
        List<Pr> out = new ArrayList<>();
        for (Pr p : prs) if (author.equals(p.author)) out.add(p);
        return out;
    }

    private List<Pr> open() {
        List<Pr> out = new ArrayList<>();
        for (Pr p : prs) if (p.status == PullRequestStatus.OPEN) out.add(p);
        return out;
    }

    private List<Pr> open(String author) {
        List<Pr> out = new ArrayList<>();
        for (Pr p : open()) if (author.equals(p.author)) out.add(p);
        return out;
    }

    /** Open, ready for review, nobody reviewed it, waiting more than 24h, and not a promotion between chosen branches. */
    private List<Pr> stale(Set<String> chosenBranches) {
        List<Pr> out = new ArrayList<>();
        for (Pr p : prs) {
            if (p.status != PullRequestStatus.OPEN || p.draft) continue;
            if (ttfr(p) != null) continue;
            if (chosenBranches.contains(p.source) && chosenBranches.contains(p.base)) continue;
            if (hours(p.created, NOW) > 24.0) out.add(p);
        }
        return out;
    }

    /** Hours from creation to the first review of someone else: active, not deleted, not a bot, not the author. */
    private Double ttfr(Pr p) {
        Instant first = null;
        for (Ev e : evs) {
            if (!e.pr.equals(p.id) || !e.active || e.deleted || e.bot) continue;
            if (e.actor != null && e.actor.equals(p.author)) continue;
            if (e.actor != null && e.actor.equalsIgnoreCase(p.login)) continue;
            if (first == null || e.at.isBefore(first)) first = e.at;
        }
        return first == null ? null : hours(p.created, first);
    }

    private List<Double> ttfrOf(List<Pr> list) {
        List<Double> out = new ArrayList<>();
        for (Pr p : list) if (!p.draft) out.add(ttfr(p));
        return out;
    }

    private static List<Double> cycleOf(List<Pr> list) {
        List<Double> out = new ArrayList<>();
        for (Pr p : list) {
            Instant end = p.merged != null ? p.merged : p.closed;
            out.add(end == null ? null : hours(p.created, end));
        }
        return out;
    }

    /** Share of the SLA-eligible PRs (reviewed, or waited more than 24h) reviewed within 24h, at 2 decimals. */
    private Double slaPercent(List<Pr> list, Instant now) {
        int eligible = 0;
        int within = 0;
        for (Pr p : list) {
            if (p.draft) continue;
            Double h = ttfr(p);
            Instant end = p.merged != null ? p.merged : p.closed != null ? p.closed : now;
            if (h != null || hours(p.created, end) > 24.0) eligible++;
            if (h != null && h <= 24.0) within++;
        }
        return eligible == 0 ? null : Math.round(100.0 * within / eligible * 100) / 100.0;
    }

    private List<Ev> reviewsGiven(String actor, Instant from, Instant to) {
        List<Ev> out = new ArrayList<>();
        for (Ev e : evs) {
            if (!actor.equals(e.actor) || !e.active || e.deleted || e.bot || !in(e.at, from, to)) continue;
            Pr pr = pr(e.pr);
            boolean own = actor.equals(pr.author);
            if (!own) out.add(e);
        }
        return out;
    }

    private Pr pr(String id) {
        for (Pr p : prs) if (p.id.equals(id)) return p;
        throw new IllegalArgumentException(id);
    }

    private static int count(List<Ev> list, ReviewKind kind) {
        int n = 0;
        for (Ev e : list) if (e.kind == kind) n++;
        return n;
    }

    private int commits(Instant from, Instant to, Predicate<Cm> filter) {
        int n = 0;
        for (Cm c : cms) if (c.active && in(c.at, from, to) && filter.test(c)) n++;
        return n;
    }

    private Map<LocalDate, Integer> commitsPerDay(LocalDate first, LocalDate last, Predicate<Cm> filter) {
        Map<LocalDate, Integer> out = new java.util.LinkedHashMap<>();
        for (LocalDate day : days(first, last)) {
            int n = 0;
            for (Cm c : cms) {
                if (c.active && filter.test(c) && c.at.atZone(ZoneOffset.UTC).toLocalDate().equals(day)) n++;
            }
            out.put(day, n);
        }
        return out;
    }

    private static Map<LocalDate, Integer> commitsByDay(List<CommitDay> chart) {
        Map<LocalDate, Integer> out = new java.util.LinkedHashMap<>();
        for (CommitDay d : chart) out.put(LocalDate.parse(d.date()), d.commits());
        return out;
    }

    private static List<LocalDate> days(LocalDate first, LocalDate last) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) out.add(d);
        return out;
    }

    /** Merges into the production branch inside [from, to): deploys/week, change failure rate and lead time. */
    private Oracle deliveryOf(Instant from, Instant to, String production) {
        List<Pr> merges = new ArrayList<>();
        for (Pr p : prs) {
            if (p.status == PullRequestStatus.MERGED && in(p.merged, from, to) && production.equals(p.base)) merges.add(p);
        }
        double days = (to.toEpochMilli() - from.toEpochMilli()) / 86_400_000.0;
        double weeks = Math.max(1.0, days / 7.0);
        double deploys = Math.round(merges.size() / weeks * 100) / 100.0;
        int remediations = 0;
        List<Double> lead = new ArrayList<>();
        for (Pr p : merges) {
            if (p.source.startsWith("hotfix/")) remediations++;
            lead.add(hours(p.earliest != null ? p.earliest : p.created, p.merged));
        }
        Double cfr = merges.isEmpty() ? null : Math.round(100.0 * remediations / merges.size() * 100) / 100.0;
        return new Oracle(merges.size(), deploys, cfr, stat(lead));
    }

    private record Oracle(int merges, double deploysPerWeek, Double cfr, DurationStat leadTime) {}

    /** Nearest-rank p50 and p90 over the non-null values. */
    private static DurationStat stat(List<Double> values) {
        List<Double> sorted = new ArrayList<>();
        for (Double v : values) if (v != null) sorted.add(v);
        if (sorted.isEmpty()) return DurationStat.empty();
        sorted.sort(Comparator.naturalOrder());
        return new DurationStat(nearestRank(sorted, 50), nearestRank(sorted, 90), sorted.size());
    }

    private static double nearestRank(List<Double> sorted, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(1, rank) - 1);
    }

    private static double hours(Instant from, Instant to) {
        return Math.round((to.toEpochMilli() - from.toEpochMilli()) / 3_600_000.0 * 10_000) / 10_000.0;
    }

    private static boolean in(Instant at, Instant from, Instant to) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }

    /** UTC when there is no zone ("2026-10-08T09:00:00"); an explicit offset ("2026-10-14T22:00-03:00") is honored. */
    private static Instant t(String iso) {
        if (iso.endsWith("Z") || iso.matches(".*T.*[+-]\\d{2}:\\d{2}$")) return OffsetDateTime.parse(iso).toInstant();
        return Instant.parse(iso + "Z");
    }

    private static TeamDashboardV2Output.Member member(TeamDashboardV2Output out, String userId) {
        return out.members().stream().filter(m -> userId.equals(m.userId())).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------
    // Fixture records and builders
    // ------------------------------------------------------------------

    private static final class Pr {
        final String id;
        final String author;
        final String login;
        final PullRequestStatus status;
        final Instant created;
        final String title;
        Instant merged;
        Instant closed;
        Instant earliest;
        String base = "main";
        String source;
        boolean draft;
        String workItem;

        Pr(String id, String author, PullRequestStatus status, String created) {
            this.id = id;
            this.author = author;
            this.login = author == null ? "ghost" : author;
            this.status = status;
            this.created = t(created);
            this.title = "PR " + id;
            this.source = "feature/" + id;
        }

        Pr merged(String at) { this.merged = t(at); return this; }
        Pr closed(String at) { this.closed = t(at); return this; }
        Pr earliest(String at) { this.earliest = t(at); return this; }
        Pr base(String branch) { this.base = branch; return this; }
        Pr source(String branch) { this.source = branch; return this; }
        Pr draft() { this.draft = true; return this; }
        Pr linked(String workItemExternalId) { this.workItem = workItemExternalId; return this; }
    }

    private static final class Ev {
        final String id;
        final String pr;
        final String actor;
        final ReviewKind kind;
        final Instant at;
        boolean bot;
        boolean deleted;
        boolean active = true;

        Ev(String id, String pr, String actor, ReviewKind kind, String at) {
            this.id = id;
            this.pr = pr;
            this.actor = actor;
            this.kind = kind;
            this.at = t(at);
        }

        Ev bot() { this.bot = true; return this; }
        Ev deleted() { this.deleted = true; return this; }
        Ev inactive() { this.active = false; return this; }
    }

    private static final class Cm {
        final String id;
        final String author;
        final Instant at;
        boolean active = true;

        Cm(String id, String author, String at) {
            this.id = id;
            this.author = author;
            this.at = t(at);
        }

        Cm inactive() { this.active = false; return this; }
    }

    private static Sprint sprint(String id, String name, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", name,
                "\\Orion\\" + name, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static User user(String id, String name, String githubId) {
        return new User(id, ORG, id + "@x.io", null, name, "role", null, githubId, null, null, null, true, true,
                0L, NOW, NOW);
    }

    private DeveloperDashboardOutput devOutput() {
        List<BurndownPoint> burndown = new ArrayList<>();
        for (int day = 0; day < 8; day++) {
            burndown.add(new BurndownPoint(LocalDate.parse("2026-10-08").plusDays(day), 0d, 0d));
        }
        return new DeveloperDashboardOutput(List.<WorkItem>of(), 15.0, 20.0, List.of(), BigDecimal.ZERO, 0, 0, List.of(),
                0, burndown, null, Map.of(), null, List.of(), null, new DurationStat(12.0, 30.0, 3),
                new DurationStat(20.0, 40.0, 3), ComplexityEffortFigures.EMPTY, null);
    }
}

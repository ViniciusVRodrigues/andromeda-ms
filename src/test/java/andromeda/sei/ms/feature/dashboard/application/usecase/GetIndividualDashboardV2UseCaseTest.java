package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageSummaryByModel;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.PendingSplit;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.AiSprintSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.v2.ComplexityEffortFigures;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.2/1.3 — individual dashboard v2, table tests ported from the preview's
 * {@code buildIndividual}: KPIs reused from the developer dashboard, items with AI cost and T/SP,
 * an unestimated item stays {@code null}, AI cost by model, reviews and commits of the caller only.
 * Now = Thursday 2026-10-08 12:00Z; the current sprint runs 2026-10-01 → 2026-10-14.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetIndividualDashboardV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final String ME = "user-a";
    private static final String OTHER = "user-b";
    private static final String REPO = "repo-1";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final Sprint S1 = sprint("s1", "Sprint 1", Sprint.TimeFrame.PAST, "2026-09-17", "2026-09-30");
    private static final Sprint S2 = sprint("s2", "Sprint 2", Sprint.TimeFrame.CURRENT, "2026-10-01", "2026-10-14");

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;
    @Mock private GetDeveloperDashboardUseCase developerDashboard;
    @Mock private SprintRepository sprintRepository;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private VcsCommitRepository commitRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;

    private GetIndividualDashboardV2UseCase useCase;
    private final List<WorkItem> devItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new GetIndividualDashboardV2UseCase(projectRepository,
                new DashboardPeopleDirectory(userRepository, connectorTokenRepository),
                developerDashboard, new DashboardSprintResolver(sprintRepository),
                new ProjectVcsActivity(vcsReadPort, commitRepository), workItemRepository,
                new EffortBearingLevelResolver(), aiUsageRepository, allocationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of(ME, OTHER), 0L, NOW, NOW)));
        User me = user(ME, "Ana Ribeiro", "gh-ana");
        when(userRepository.findByIdAndOrganizationId(ME, ORG)).thenReturn(Optional.of(me));
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(me, user(OTHER, "Bruno Lima", null)));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(S1, S2));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s2", ORG)).thenReturn(Optional.of(S2));
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(new VcsRepository(REPO, ORG,
                VcsProvider.GITHUB, "ext-repo", "owner/orion")));
        when(developerDashboard.execute(ORG, ME, PROJECT, "s2", null, null, null))
                .thenAnswer(inv -> devOutput(10.0, 5.0, null));
    }

    @Test
    void should_throwSprintNotFound_when_sprintIsOfAnotherProject() {
        Sprint foreign = sprint("s-x", "Sprint X", Sprint.TimeFrame.CURRENT, "2026-10-01", "2026-10-14");
        foreign.setProjectId("project-2");
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s-x", ORG)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> useCase.execute(ORG, ME, PROJECT, "s-x"))
                .isInstanceOf(SprintNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "SPRINT_NOT_FOUND");
        verify(developerDashboard, never()).execute(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_throwProjectNotFound_when_projectIsNotOfTheOrganization() {
        assertThatThrownBy(() -> useCase.execute(ORG, ME, "project-of-other-org", null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_passTheTargetToTheRepositories_when_callerLooksAtAnotherMember() {
        when(userRepository.findByIdAndOrganizationId(OTHER, ORG)).thenReturn(Optional.of(user(OTHER, "Bruno Lima", null)));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, OTHER, PROJECT, null);

        assertThat(out.person().id()).isEqualTo(OTHER);
        assertThat(out.person().name()).isEqualTo("Bruno Lima");
    }

    @Test
    void should_useTheCaller_when_targetIsNullOrTheCaller() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());

        assertThat(useCase.execute(ORG, ME, null, PROJECT, null).person().id()).isEqualTo(ME);
        assertThat(useCase.execute(ORG, ME, ME, PROJECT, null).person().id()).isEqualTo(ME);
    }

    @Test
    void should_throwMemberNotFound_when_targetIsNotAMemberOfTheProject() {
        when(userRepository.findByIdAndOrganizationId("user-z", ORG)).thenReturn(Optional.of(user("user-z", "Zed", null)));

        assertThatThrownBy(() -> useCase.execute(ORG, ME, "user-z", PROJECT, null))
                .isInstanceOf(andromeda.sei.ms.feature.project.domain.exception.ProjectMemberNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PROJECT_MEMBER_NOT_FOUND");
        verify(developerDashboard, never()).execute(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_throwMemberNotFound_when_targetIsAMemberButInactiveOrUnknownInTheOrganization() {
        User inactive = new User(OTHER, ORG, "b@x.io", null, "Bruno", "role", null, null, null, null, null, true,
                false, 0L, NOW, NOW);
        when(userRepository.findByIdAndOrganizationId(OTHER, ORG)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> useCase.execute(ORG, ME, OTHER, PROJECT, null))
                .hasFieldOrPropertyWithValue("code", "PROJECT_MEMBER_NOT_FOUND");

        when(userRepository.findByIdAndOrganizationId(OTHER, ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(ORG, ME, OTHER, PROJECT, null))
                .hasFieldOrPropertyWithValue("code", "PROJECT_MEMBER_NOT_FOUND");
    }

    @Test
    void should_throwProjectNotFound_beforeCheckingMembership_when_projectIsOfAnotherOrganization() {
        assertThatThrownBy(() -> useCase.execute(ORG, ME, OTHER, "project-of-other-org", null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_returnPayloadWithoutSprint_when_projectHasNoSprint() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.sprint()).isNull();
        assertThat(out.kpis()).isNull();
        assertThat(out.previous()).isNull();
        assertThat(out.items()).isEmpty();
        assertThat(out.history()).isEmpty();
        assertThat(out.person().id()).isEqualTo(ME);
        assertThat(out.asOf()).isEqualTo(NOW);
    }

    @Test
    void should_mapItems_withAiCostPerItem_tokensPerPoint_andNullWhenUnestimated() {
        WorkItem done = WorkItemFixture.item("wi-1", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE, 5.0, null);
        done.setTokensPerPoint(1200.0);
        done.setAssigneeUserId(ME);
        WorkItem unestimated = WorkItemFixture.item("wi-2", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.IN_PROGRESS);
        unestimated.setStartedAt(Instant.parse("2026-10-06T12:00:00Z"));
        unestimated.setAssigneeUserId(ME);
        devItems.addAll(List.of(done, unestimated));
        when(developerDashboard.execute(ORG, ME, PROJECT, "s2", null, null, null))
                .thenAnswer(inv -> devOutput(5.0, 5.0, Map.of("wi-1", new BigDecimal("3.50"))));
        VcsPullRequest linked = pr("pr-1", ME, PullRequestStatus.MERGED, "2026-10-02T10:00:00Z");
        linked.setLinkedWorkItemExternalId("ext-wi-1");
        linked.setMergedAt(Instant.parse("2026-10-03T10:00:00Z"));
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(linked));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.items()).extracting(ItemView::id).containsExactly("wi-2", "wi-1");
        ItemView inProgress = out.items().get(0);
        assertThat(inProgress.effort()).isNull();
        assertThat(inProgress.tokensPerPoint()).isNull();
        assertThat(inProgress.aiCostUsd()).isNull();
        assertThat(inProgress.daysInProgress()).isEqualTo(2.0);
        ItemView delivered = out.items().get(1);
        assertThat(delivered.aiCostUsd()).isEqualByComparingTo("3.50");
        assertThat(delivered.tokensPerPoint()).isEqualTo(1200.0);
        assertThat(delivered.daysInProgress()).isNull();
        assertThat(delivered.assignee().name()).isEqualTo("Ana Ribeiro");
        assertThat(delivered.pr().number()).isEqualTo(1);
        assertThat(delivered.pr().repo()).isEqualTo("owner/orion");
        assertThat(delivered.pr().stale()).isFalse();
        assertThat(out.kpis().itemsDone()).isEqualTo(1);
    }

    /** complexity-effort 3.1: the person's figures reach the kpis and the burndown carries points. */
    @Test
    void should_exposeComplexityEffortAndAiRatios_inKpis_andPointsInTheBurndown() {
        figures = new ComplexityEffortFigures(3.0, 3.0, 12.0, 2000.0, 500.0, 0, 1);

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.kpis().complexityPlanned()).isEqualTo(3.0);
        assertThat(out.kpis().complexityDelivered()).isEqualTo(3.0);
        assertThat(out.kpis().effortLogged()).isEqualTo(12.0);
        assertThat(out.kpis().avgTokensPerPoint()).isEqualTo(2000.0);
        assertThat(out.kpis().avgTokensPerHour()).isEqualTo(500.0);
        assertThat(out.kpis().unitsWithoutComplexity()).isZero();
        assertThat(out.burndown().get(0).remainingPoints()).isEqualTo(10.0);
        assertThat(out.burndown().get(0).idealPoints()).isEqualTo(10.0);
    }

    /** complexity-effort D1: the list shows the Story (points + summed Task hours), not its Tasks; orphan Tasks stay. */
    @Test
    void should_listTheStoryWithPointsAndRolledUpHours_notItsTasks_andKeepOrphanTasks() {
        WorkItem story = WorkItemFixture.item("st", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.IN_PROGRESS);
        story.setComplexity(3.0);
        story.setStartedAt(Instant.parse("2026-10-06T12:00:00Z"));
        WorkItem t1 = WorkItemFixture.item("t1", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 4.0, "ext-st");
        WorkItem t2 = WorkItemFixture.item("t2", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 8.0, "ext-st");
        WorkItem orphan = WorkItemFixture.item("or", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.TODO, 2.0, null);
        devItems.addAll(List.of(story, t1, t2, orphan));
        when(developerDashboard.execute(ORG, ME, PROJECT, "s2", null, null, null))
                .thenAnswer(inv -> devOutput(3.0, 0.0, Map.of()));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.items()).extracting(ItemView::id).containsExactlyInAnyOrder("st", "or");
        ItemView row = out.items().stream().filter(i -> i.id().equals("st")).findFirst().orElseThrow();
        assertThat(row.complexity()).isEqualTo(3.0);
    }

    @Test
    void should_notFlagMergedPrWithoutReviewAsStale_inPullRequests() {
        VcsPullRequest merged = pr("pr-2", ME, PullRequestStatus.MERGED, "2026-10-02T10:00:00Z");
        merged.setMergedAt(Instant.parse("2026-10-05T10:00:00Z"));
        VcsPullRequest open = pr("pr-1", ME, PullRequestStatus.OPEN, "2026-10-02T10:00:00Z");
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(open, merged));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.pullRequests()).extracting(p -> p.id()).containsExactly("pr-1", "pr-2");
        assertThat(out.pullRequests()).extracting(p -> p.stale()).containsExactly(true, false);
    }

    @Test
    void should_returnNullPrsOpen_when_sprintIsPast_likeTheTeamView() {
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s1", ORG)).thenReturn(Optional.of(S1));
        when(developerDashboard.execute(ORG, ME, PROJECT, "s1", null, null, null))
                .thenAnswer(inv -> devOutput(5.0, 5.0, null));
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO))
                .thenReturn(List.of(pr("pr-1", ME, PullRequestStatus.OPEN, "2026-09-01T10:00:00Z")));

        IndividualDashboardV2Output past = useCase.execute(ORG, ME, PROJECT, "s1");
        IndividualDashboardV2Output current = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(past.kpis().prsOpen()).isNull();
        assertThat(current.kpis().prsOpen()).isEqualTo(1);
    }

    @Test
    void should_reuseDeveloperKpis_countOpenPrsOfAnyAge_andKeepAiCostNull_withoutUsage() {
        VcsPullRequest oldOpen = pr("pr-1", ME, PullRequestStatus.OPEN, "2026-09-01T10:00:00Z");
        VcsPullRequest mergedInSprint = pr("pr-2", ME, PullRequestStatus.MERGED, "2026-10-02T10:00:00Z");
        mergedInSprint.setMergedAt(Instant.parse("2026-10-05T10:00:00Z"));
        mergedInSprint.setTimeToFirstReviewHours(4.0);
        VcsPullRequest othersOpen = pr("pr-3", OTHER, PullRequestStatus.OPEN, "2026-10-02T10:00:00Z");
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(oldOpen, mergedInSprint, othersOpen));

        figures = new ComplexityEffortFigures(10.0, 5.0, 12.0, 2000.0, 500.0, 0, 1);

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.kpis().complexityPlanned()).isEqualTo(10.0);
        assertThat(out.kpis().complexityDelivered()).isEqualTo(5.0);
        assertThat(out.kpis().cycleTimeHours()).isEqualTo(new DurationStat(12.0, 30.0, 3));
        assertThat(out.kpis().prsOpen()).isEqualTo(1);
        assertThat(out.kpis().prsMerged()).isEqualTo(1);
        assertThat(out.kpis().timeToFirstReviewHours().p50()).isEqualTo(4.0);
        assertThat(out.kpis().aiCostUsd()).isNull();
        assertThat(out.kpis().aiTokens()).isNull();
        assertThat(out.pullRequests()).extracting(p -> p.id()).containsExactly("pr-1", "pr-2");
        assertThat(out.pullRequests().get(0).hoursOpen()).isGreaterThan(24.0 * 30);
        assertThat(out.sprint().dayIndex()).isEqualTo(6);
        assertThat(out.sprint().dayCount()).isEqualTo(10);
    }

    @Test
    void should_labelPullRequestAuthor_byLogin_when_accountIsNotLinked() {
        VcsPullRequest unlinked = pr("pr-9", null, PullRequestStatus.OPEN, "2026-10-02T10:00:00Z");
        unlinked.setAuthorLogin("ghost");
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(unlinked));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        // Not the caller's PR: it never enters the individual list nor the open count.
        assertThat(out.pullRequests()).isEmpty();
        assertThat(out.kpis().prsOpen()).isZero();
    }

    @Test
    void should_breakDownAiCostByModel_sortedByCost_andSumTheSprintUsage() {
        when(developerDashboard.execute(ORG, ME, PROJECT, "s2", null, null, null)).thenAnswer(inv -> new DeveloperDashboardOutput(
                List.of(), null, null,
                List.of(new DailyAiUsage(LocalDate.parse("2026-10-01"), 100, 50, 3, new BigDecimal("1.50"))),
                new BigDecimal("1.50"), 0, 0, List.of(), 0, List.of(), new AiSprintSummary(150, new BigDecimal("1.50"), 0),
                Map.of(), null, List.of(), null, DurationStat.empty(), DurationStat.empty()));
        when(aiUsageRepository.findSummaryGroupedByModel(ORG, ME, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-14"), null))
                .thenReturn(List.of(new AiUsageSummaryByModel("gpt-5", 100, 20, 4, new BigDecimal("0.40")),
                        new AiUsageSummaryByModel("claude", 30, 0, 2, new BigDecimal("1.10"))));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.ai().byModel()).extracting(m -> m.model()).containsExactly("claude", "gpt-5");
        assertThat(out.ai().byModel().get(1).tokens()).isEqualTo(120);
        assertThat(out.ai().byModel().get(1).costUsd()).isEqualByComparingTo("0.40");
        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("1.50");
        assertThat(out.kpis().aiTokens()).isEqualTo(150L);
        assertThat(out.ai().byDay()).hasSize(1);
        assertThat(out.ai().byDay().get(0).label()).isEqualTo("01/10");
    }

    @Test
    void should_summarizeAllocationDays_andListPendingSplits() {
        AIUsage validated = usage("u1", "2026-10-01", "a-valid");
        AIUsage pending = usage("u2", "2026-10-02", "a-pending");
        AIUsage free = usage("u3", "2026-10-05", null);
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(eq(ORG), eq(ME), any(), any()))
                .thenReturn(List.of(validated, pending, free));
        when(allocationRepository.findByOrgUserAndStatus(ORG, ME, AiUsageAllocation.Status.VALIDATED))
                .thenReturn(List.of(allocation("a-valid", "2026-10-01", "u1", AiUsageAllocation.Status.VALIDATED)));
        when(allocationRepository.findByOrgUserAndStatus(ORG, ME, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(allocation("a-pending", "2026-10-02", "u2", AiUsageAllocation.Status.PENDING_VALIDATION),
                        allocation("a-old", "2026-08-01", "u0", AiUsageAllocation.Status.PENDING_VALIDATION)));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.ai().allocation().validatedDays()).isEqualTo(1);
        assertThat(out.ai().allocation().pendingDays()).isEqualTo(1);
        assertThat(out.ai().allocation().unallocatedDays()).isEqualTo(1);
        assertThat(out.ai().pendingAllocations()).hasSize(1);
        var day = out.ai().pendingAllocations().get(0);
        assertThat(day.day()).isEqualTo("2026-10-02");
        assertThat(day.cost()).isEqualByComparingTo("2.00");
        assertThat(day.tokens()).isEqualTo(30);
        assertThat(day.splits().get(0).percentage()).isEqualTo(0.25);
        assertThat(out.ai().editors()).containsExactly("vscode");
    }

    @Test
    void should_countPendingSuggestionWithoutStampedUsage_andListUnallocatedDays() {
        AIUsage validated = usage("u1", "2026-10-01", "a-valid");
        AIUsage pending = usage("u2", "2026-10-02", null);
        AIUsage freeNew = usage("u3", "2026-10-05", null);
        AIUsage freeOld = usage("u4", "2026-10-03", null);
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(eq(ORG), eq(ME), any(), any()))
                .thenReturn(List.of(validated, pending, freeNew, freeOld));
        when(allocationRepository.findByOrgUserAndStatus(ORG, ME, AiUsageAllocation.Status.VALIDATED))
                .thenReturn(List.of(allocation("a-valid", "2026-10-01", "u1", AiUsageAllocation.Status.VALIDATED)));
        AiUsageAllocation suggestion = allocation("a-pending", "2026-10-02", "u2",
                AiUsageAllocation.Status.PENDING_VALIDATION);
        suggestion.setSuggestionSource(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
        when(allocationRepository.findByOrgUserAndStatus(ORG, ME, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(suggestion));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.ai().allocation().validatedDays()).isEqualTo(1);
        assertThat(out.ai().allocation().pendingDays()).isEqualTo(1);
        assertThat(out.ai().allocation().unallocatedDays()).isEqualTo(2);
        assertThat(out.ai().allocation().pendingDays()).isEqualTo(out.ai().pendingAllocations().size());
        assertThat(out.ai().allocation().unallocatedDays()).isEqualTo(out.ai().unallocatedDayList().size());
        var day = out.ai().pendingAllocations().get(0);
        assertThat(day.allocationId()).isEqualTo("a-pending");
        assertThat(day.day()).isEqualTo("2026-10-02");
        assertThat(day.cost()).isEqualByComparingTo("2.00");
        assertThat(day.suggestionSource()).isEqualTo("AZURE_ACTIVITY");
        assertThat(day.splits()).extracting(PendingSplit::workItemId).containsExactly("wi-1", "wi-2");
        assertThat(day.splits().get(0).percentage()).isEqualTo(0.25);
        assertThat(out.ai().unallocatedDayList()).extracting(IndividualDashboardV2Output.UnallocatedDay::day)
                .containsExactly("2026-10-05", "2026-10-03");
        assertThat(out.ai().unallocatedDayList().get(0).tokens()).isEqualTo(30);
        assertThat(out.ai().unallocatedDayList().get(0).cost()).isEqualByComparingTo("2.00");
    }

    @Test
    void should_readLegacySuggestionWithoutSourceAsInProgress() {
        AIUsage pending = usage("u2", "2026-10-02", null);
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(eq(ORG), eq(ME), any(), any()))
                .thenReturn(List.of(pending));
        when(allocationRepository.findByOrgUserAndStatus(ORG, ME, AiUsageAllocation.Status.PENDING_VALIDATION))
                .thenReturn(List.of(allocation("a-pending", "2026-10-02", "u2",
                        AiUsageAllocation.Status.PENDING_VALIDATION)));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.ai().pendingAllocations()).singleElement()
                .satisfies(p -> assertThat(p.suggestionSource()).isEqualTo("IN_PROGRESS"));
        assertThat(out.ai().unallocatedDayList()).isEmpty();
    }

    @Test
    void should_countReviewsGivenByTheCaller_only_byKind() {
        VcsPullRequest othersPr = pr("pr-3", OTHER, PullRequestStatus.OPEN, "2026-10-02T10:00:00Z");
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(othersPr));
        when(vcsReadPort.reviewEvents(eq(ORG), any())).thenReturn(List.of(
                review("r1", ME, ReviewKind.APPROVED, "2026-10-03T10:00:00Z"),
                review("r2", ME, ReviewKind.APPROVED_WITH_SUGGESTIONS, "2026-10-03T11:00:00Z"),
                review("r3", ME, ReviewKind.CHANGES_REQUESTED, "2026-10-04T10:00:00Z"),
                review("r4", ME, ReviewKind.COMMENTED, "2026-10-04T11:00:00Z"),
                review("r5", ME, ReviewKind.REJECTED, "2026-10-04T12:00:00Z"),
                review("r6", OTHER, ReviewKind.APPROVED, "2026-10-04T12:00:00Z"),
                review("r7", ME, ReviewKind.APPROVED, "2026-09-20T12:00:00Z")));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.reviewsGiven()).isEqualTo(new IndividualDashboardV2Output.ReviewsGiven(5, 2, 1, 1, 1));
    }

    @Test
    void should_countCommitsPerDayOfTheCaller_upToToday() {
        when(commitRepository.findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(eq(ORG), eq(List.of(REPO)),
                eq(Instant.parse("2026-10-01T00:00:00Z")), eq(Instant.parse("2026-10-15T00:00:00Z"))))
                .thenReturn(List.of(commit("c1", ME, "2026-10-02T09:00:00Z"), commit("c2", ME, "2026-10-02T17:00:00Z"),
                        commit("c3", OTHER, "2026-10-02T10:00:00Z"), commit("c4", null, "2026-10-03T10:00:00Z")));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.commitsByDay()).hasSize(8);
        assertThat(out.commitsByDay().get(1).date()).isEqualTo("2026-10-02");
        assertThat(out.commitsByDay().get(1).commits()).isEqualTo(2);
        assertThat(out.commitsByDay().get(2).commits()).isZero();
    }

    @Test
    void should_extendBurndownToSprintEnd_withNullRemaining_forDaysToCome() {
        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.burndown()).hasSize(14);
        assertThat(out.burndown().get(0).remainingPoints()).isEqualTo(10.0);
        assertThat(out.burndown().get(7).date()).isEqualTo("2026-10-08");
        assertThat(out.burndown().get(7).remainingPoints()).isEqualTo(5.0);
        assertThat(out.burndown().get(13).date()).isEqualTo("2026-10-14");
        assertThat(out.burndown().get(13).label()).isEqualTo("14/10");
        assertThat(out.burndown().get(13).remainingPoints()).isNull();
        assertThat(out.burndown().get(13).idealPoints()).isEqualTo(0.0);
    }

    @Test
    void should_keepBurndownRemainingNull_when_noUnitCarriesEffort() {
        when(developerDashboard.execute(ORG, ME, PROJECT, "s2", null, null, null))
                .thenAnswer(inv -> devOutput(null, null, null));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.burndown()).isNotEmpty().allSatisfy(day -> assertThat(day.remainingPoints()).isNull());
        assertThat(out.kpis().complexityPlanned()).isNull();
    }

    @Test
    void should_computePreviousAndHistory_withTheSingleVelocityDefinition() {
        WorkItem s1Done = WorkItemFixture.item("wi-10", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE, 3.0, null);
        s1Done.setCycleTimeHours(20.0);
        s1Done.setComplexity(3.0);
        WorkItem s1Todo = WorkItemFixture.item("wi-11", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO, 2.0, null);
        s1Todo.setComplexity(2.0);
        WorkItem s1Unestimated = WorkItemFixture.item("wi-12", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(ORG, ME, "s1", PROJECT))
                .thenReturn(List.of(s1Done, s1Todo, s1Unestimated));
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(ORG, ME, "s2", PROJECT))
                .thenReturn(List.of());

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.previous().complexityPlanned()).isEqualTo(5.0);
        assertThat(out.previous().complexityDelivered()).isEqualTo(3.0);
        assertThat(out.previous().itemsDone()).isEqualTo(1);
        assertThat(out.previous().itemsTotal()).isEqualTo(3);
        assertThat(out.previous().cycleTimeHours()).isEqualTo(new DurationStat(20.0, 20.0, 1));
        assertThat(out.history()).extracting(h -> h.sprintId()).containsExactly("s1", "s2");
        assertThat(out.history().get(1).isSelected()).isTrue();
        assertThat(out.history().get(1).isCurrent()).isTrue();
        assertThat(out.history().get(1).label()).isEqualTo("S2*");
        assertThat(out.history().get(1).complexityPlanned()).isNull();
        assertThat(out.history().get(0).label()).isEqualTo("S1");
    }

    @Test
    void should_exposePersonLinks() {
        when(connectorTokenRepository.findActiveByOrganizationIdAndUserId(ORG, ME))
                .thenReturn(List.of(new ConnectorToken("t1", ORG, ME, "laptop", "hash", true, 0L, NOW, NOW)));

        IndividualDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, null);

        assertThat(out.person()).isEqualTo(new IndividualDashboardV2Output.Person(ME, "Ana Ribeiro", true, true, true));
    }

    private DeveloperDashboardOutput devOutput(Double planned, Double delivered, Map<String, BigDecimal> aiCostByItem) {
        List<BurndownPoint> burndown = new ArrayList<>();
        for (int day = 0; day < 8; day++) {
            double total = planned == null ? 0d : planned;
            burndown.add(new BurndownPoint(LocalDate.parse("2026-10-01").plusDays(day),
                    day == 7 && delivered != null ? total - delivered : total, total * (1 - day / 13.0)));
        }
        return new DeveloperDashboardOutput(List.copyOf(devItems), 15.0, 20.0, List.of(),
                BigDecimal.ZERO, 0, 0, List.of(), devItems.size(), burndown, null,
                aiCostByItem == null ? Map.of() : aiCostByItem, null, List.of(), null,
                new DurationStat(12.0, 30.0, 3), new DurationStat(20.0, 40.0, 3), figures, null);
    }

    private ComplexityEffortFigures figures = ComplexityEffortFigures.EMPTY;

    private static Sprint sprint(String id, String name, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", name,
                "\\Orion\\" + name, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static User user(String id, String name, String githubId) {
        return new User(id, ORG, id + "@x.io", null, name, "role", null, githubId, null, null, null, true, true,
                0L, NOW, NOW);
    }

    private static VcsPullRequest pr(String id, String authorUserId, PullRequestStatus status, String createdAt) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, REPO, VcsProvider.GITHUB, "ext-" + id,
                Integer.parseInt(id.replaceAll("\\D", "")), "PR " + id, status);
        pr.setAuthorUserId(authorUserId);
        pr.setAuthorLogin(authorUserId == null ? "ghost" : authorUserId);
        pr.setCreatedAt(Instant.parse(createdAt));
        return pr;
    }

    private static VcsReviewEvent review(String id, String actor, ReviewKind kind, String at) {
        VcsReviewEvent event = new VcsReviewEvent(id, ORG, "pr-3", REPO, VcsProvider.GITHUB, "ext-" + id, kind,
                Instant.parse(at));
        event.setActorUserId(actor);
        return event;
    }

    private static VcsCommit commit(String sha, String author, String at) {
        VcsCommit commit = new VcsCommit(sha, ORG, REPO, VcsProvider.GITHUB, sha);
        commit.setAuthorUserId(author);
        commit.setAuthoredAt(Instant.parse(at));
        return commit;
    }

    private static AIUsage usage(String id, String day, String allocationId) {
        return new AIUsage(id, ORG, ME, "gh", "ds", LocalDate.parse(day), "gpt-5", "w", "w", "m", "m",
                20, 10, 1, "vscode", allocationId, new BigDecimal("2.00"), NOW, true, 0L, NOW, NOW);
    }

    private static AiUsageAllocation allocation(String id, String day, String usageId, AiUsageAllocation.Status status) {
        return new AiUsageAllocation(id, ORG, ME, LocalDate.parse(day), List.of(usageId),
                List.of(new AllocationSplit("wi-1", "Login", new BigDecimal("0.25")),
                        new AllocationSplit("wi-2", "Logout", new BigDecimal("0.75"))),
                status, AiUsageAllocation.AssignedBy.AUTO, true, 0L, NOW, NOW);
    }
}

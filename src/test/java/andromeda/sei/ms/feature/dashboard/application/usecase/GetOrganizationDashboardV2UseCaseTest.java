package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.PortfolioRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ProjectCost;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.SyncOutcomeDay;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.dashboard.domain.exception.DashboardPeriodInvalidException;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.11 — organization overview v2, table tests ported from the preview's
 * {@code buildOrganization}: only the visible projects are aggregated (mutation "all projects"),
 * portfolio health, throughput per project, consumption × subscription, fluency distribution, AI
 * cost per project and the sync runs. Now = Sunday 2026-10-11 12:00Z; 90 days = 2026-07-14 →
 * 2026-10-11 (weekly buckets from 20/07), previous = 2026-04-15 → 2026-07-13.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetOrganizationDashboardV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String A = "p-a";
    private static final String B = "p-b";
    private static final String C = "p-c";

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private VcsCommitRepository commitRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;
    @Mock private FluencyScoreRepository fluencyScoreRepository;
    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private AzureProjectBindingRepository azureBindingRepository;
    @Mock private IntegrationSyncLogRepository syncLogRepository;

    private final Map<String, List<WorkItem>> itemsByProject = new HashMap<>();
    private final List<Sprint> sprints = new ArrayList<>();
    private final List<AIUsage> usage = new ArrayList<>();
    private final List<VcsPullRequest> prs = new ArrayList<>();
    private GetOrganizationDashboardV2UseCase useCase;

    @BeforeEach
    void setUp() {
        OrganizationDashboardData data = new OrganizationDashboardData(projectRepository, userRepository,
                workItemRepository, sprintRepository, new EffortBearingLevelResolver(), bindingRepository,
                repositoryRepository, vcsReadPort, commitRepository,
                new DeliveryMetricsService(vcsReadPort, workItemRepository, organizationRepository),
                organizationRepository, aiUsageRepository, allocationRepository, connectorTokenRepository,
                fluencyScoreRepository, azureConnectionRepository, githubConnectionRepository, azureBindingRepository,
                syncLogRepository);
        useCase = new GetOrganizationDashboardV2UseCase(data, Clock.fixed(NOW, ZoneOffset.UTC));
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(
                project(C, "Cygnus", "u-c"), project(A, "Andromeda", "u-a", "u-shared"),
                project(B, "Bootes", "u-b", "u-shared")));
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(user("u-a", "az", "gh"),
                user("u-b", null, "gh"), user("u-c", "az", null), user("u-shared", null, null),
                user("u-loner", null, null)));
        for (String p : List.of(A, B, C)) {
            itemsByProject.put(p, new ArrayList<>());
            when(workItemRepository.findMetricFieldsByOrganizationIdAndProjectId(ORG, p)).thenReturn(itemsByProject.get(p));
            when(workItemRepository.findActiveIdsByOrganizationIdAndProjectId(ORG, p)).thenAnswer(inv -> itemsByProject
                    .get(p).stream().map(WorkItem::getId).collect(java.util.stream.Collectors.toSet()));
        }
        when(sprintRepository.findAllByOrganizationId(ORG)).thenReturn(sprints);
        when(aiUsageRepository.findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(eq(ORG), anyCollection(), any(), any()))
                .thenAnswer(inv -> {
                    Collection<String> users = inv.getArgument(1);
                    LocalDate from = inv.getArgument(2);
                    LocalDate to = inv.getArgument(3);
                    return usage.stream().filter(u -> users.contains(u.getUserId()) && !u.getDay().isBefore(from)
                            && !u.getDay().isAfter(to)).toList();
                });
        when(vcsReadPort.pullRequestMetricFieldsTouchedSince(eq(ORG), anyCollection(), any())).thenAnswer(inv -> {
            Collection<String> repositories = inv.getArgument(1);
            return prs.stream().filter(pr -> repositories.contains(pr.getRepositoryId())).toList();
        });
        when(syncLogRepository.countByProjectDayAndStatus(eq(ORG), anyCollection(), any())).thenCallRealMethod();
        when(aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(1);
            return usage.stream().filter(u -> ids.contains(u.getId())).toList();
        });
        when(aiUsageRepository.summarizeTotalsByUser(eq(ORG), anyCollection(), any(), any())).thenAnswer(inv -> {
            Collection<String> users = inv.getArgument(1);
            LocalDate from = inv.getArgument(2);
            LocalDate to = inv.getArgument(3);
            return usage.stream().filter(u -> users.contains(u.getUserId()) && !u.getDay().isBefore(from)
                            && !u.getDay().isAfter(to))
                    .collect(java.util.stream.Collectors.groupingBy(AIUsage::getUserId)).entrySet().stream()
                    .map(e -> new andromeda.sei.ms.feature.aiusage.domain.AiUsageUserTotals(e.getKey(),
                            e.getValue().stream().map(AIUsage::getCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add),
                            e.getValue().stream().mapToLong(AIUsage::getInputTokens).sum(),
                            e.getValue().stream().mapToLong(AIUsage::getOutputTokens).sum()))
                    .toList();
        });
        when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());
    }

    @Test
    void should_rejectAPeriodOutsideTheAllowedSet() {
        assertThatThrownBy(() -> useCase.execute(ORG, Set.of(A), 45)).isInstanceOf(DashboardPeriodInvalidException.class);
    }

    /** Mutation "aggregate every project": C's items, members and cost must not reach any figure. */
    @Test
    void should_aggregateOnlyTheVisibleProjects() {
        itemsByProject.get(A).add(done("a1", A, WorkItem.ResolvedType.STORY, 3.0, "2026-08-04T10:00:00Z"));
        itemsByProject.get(B).add(done("b1", B, WorkItem.ResolvedType.BUG, 2.0, "2026-08-05T10:00:00Z"));
        itemsByProject.get(C).add(done("c1", C, WorkItem.ResolvedType.STORY, 8.0, "2026-08-06T10:00:00Z"));
        itemsByProject.get(C).add(done("c2", C, WorkItem.ResolvedType.TASK, 5.0, "2026-08-06T10:00:00Z"));
        usage.add(usage("au1", "u-a", "2026-08-04", null, "2.00", "claude"));
        usage.add(usage("au2", "u-c", "2026-08-04", null, "50.00", "claude"));

        OrganizationDashboardV2Output out = useCase.execute(ORG, Set.of(A, B), 90);

        assertThat(out.portfolio()).extracting(PortfolioRow::projectId).containsExactly(A, B);
        assertThat(out.kpis().projects()).isEqualTo(2);
        assertThat(out.kpis().itemsDone()).isEqualTo(2);
        assertThat(out.kpis().spDelivered()).isEqualTo(5.0);
        assertThat(out.kpis().people()).as("members of A ∪ B").isEqualTo(3);
        // partial caller: the cost allocated to A and B (none here), never the people's whole usage
        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("0");
        assertThat(out.ai().subscription().consumptionUsd()).isEqualByComparingTo("2.00");
        assertThat(out.delivery().throughputByProject().get(2).byProject()).containsOnlyKeys(A, B);
        assertThat(out.ai().costByProject()).extracting(ProjectCost::projectId).containsExactly(A, B, null);
        assertThat(out.integrations().projects()).extracting("projectId").containsExactly(A, B);
        verify(workItemRepository, never()).findMetricFieldsByOrganizationIdAndProjectId(ORG, C);
        verify(workItemRepository, never()).findActiveIdsByOrganizationIdAndProjectId(ORG, C);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> people = ArgumentCaptor.forClass(Collection.class);
        verify(aiUsageRepository, org.mockito.Mockito.atLeastOnce())
                .findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(eq(ORG), people.capture(), any(), any());
        assertThat(people.getAllValues().stream().flatMap(Collection::stream).distinct().toList())
                .containsExactlyInAnyOrder("u-a", "u-b", "u-shared");
    }

    @Test
    void should_countEveryActiveUser_when_theCallerSeesEveryActiveProject() {
        OrganizationDashboardV2Output out = useCase.execute(ORG, Set.of(A, B, C, "p-archived"), 90);

        assertThat(out.kpis().people()).isEqualTo(5);
        assertThat(out.portfolio()).hasSize(3);
    }

    @Test
    void should_answerEmptyFigures_when_noProjectIsVisible() {
        OrganizationDashboardV2Output out = useCase.execute(ORG, Set.of(), 90);

        assertThat(out.portfolio()).isEmpty();
        assertThat(out.kpis().itemsDone()).isZero();
        assertThat(out.kpis().spDelivered()).isNull();
        assertThat(out.kpis().leadTimeHours().n()).isZero();
        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("0");
        assertThat(out.kpis().aiCostPerSp()).isNull();
        assertThat(out.delivery().flow()).hasSize(12);
        assertThat(out.ai().costByProject()).singleElement().extracting(ProjectCost::projectId).isNull();
        verify(workItemRepository, never()).findMetricFieldsByOrganizationIdAndProjectId(anyString(), anyString());
        verify(aiUsageRepository, never()).findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(any(), anyCollection(), any(), any());
    }

    @Test
    void should_rateThePortfolioHealth_fromTheCurrentAndTheLastSprint() {
        sprints.add(sprint("s1", A, Sprint.TimeFrame.PAST, "2026-09-21", "2026-10-02"));
        sprints.add(sprint("s2", A, Sprint.TimeFrame.CURRENT, "2026-10-05", "2026-10-16"));
        itemsByProject.get(A).addAll(List.of(
                inSprint(done("a1", A, WorkItem.ResolvedType.STORY, 5.0, "2026-09-25T10:00:00Z"), "s1"),
                inSprint(item("a2", A, WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO, 5.0), "s1"),
                inSprint(item("a3", A, WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO, 8.0), "s2"),
                inSprint(item("a4", A, WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO, 2.0), "s2")));
        bind("r1", A);
        for (int i = 1; i <= 3; i++) prs.add(pr("pr" + i, "r1", "2026-09-20T10:00:00Z", null));

        PortfolioRow row = useCase.execute(ORG, Set.of(A), 90).portfolio().getFirst();

        assertThat(row.team()).isEqualTo("Core");
        assertThat(row.lastPredictabilityPct()).isEqualTo(50.0);
        assertThat(row.currentSprint().name()).isEqualTo("s2");
        assertThat(row.currentSprint().spPlanned()).isEqualTo(10.0);
        assertThat(row.currentSprint().spDone()).isEqualTo(0.0);
        assertThat(row.currentSprint().progressPct()).isEqualTo(0.0);
        // Mon 05/10 → Sun 11/10: 5 of 10 working days
        assertThat(row.currentSprint().elapsedPct()).isEqualTo(50.0);
        assertThat(row.currentProgressGapPct()).isEqualTo(50.0);
        assertThat(row.stalePrs()).isEqualTo(3);
        assertThat(row.health().level()).isEqualTo("risk");
        assertThat(row.health().reasons()).hasSize(3);
    }

    @Test
    void should_leaveTheCurrentSprintNull_when_theProjectHasNone() {
        PortfolioRow row = useCase.execute(ORG, Set.of(B), 90).portfolio().getFirst();

        assertThat(row.currentSprint()).isNull();
        assertThat(row.currentProgressGapPct()).isNull();
        assertThat(row.lastPredictabilityPct()).isNull();
        assertThat(row.health().level()).isEqualTo("ok");
    }

    @Test
    void should_countThroughputPerProjectAndBucket_andTheTypeMix() {
        itemsByProject.get(A).add(done("a1", A, WorkItem.ResolvedType.STORY, 3.0, "2026-07-21T10:00:00Z"));
        itemsByProject.get(A).add(done("a2", A, WorkItem.ResolvedType.BUG, 1.0, "2026-07-22T10:00:00Z"));
        itemsByProject.get(B).add(done("b1", B, WorkItem.ResolvedType.TASK, 2.0, "2026-07-23T10:00:00Z"));
        itemsByProject.get(B).add(done("b0", B, WorkItem.ResolvedType.STORY, 2.0, "2026-05-01T10:00:00Z"));

        OrganizationDashboardV2Output out = useCase.execute(ORG, Set.of(A, B), 90);

        var first = out.delivery().throughputByProject().getFirst();
        assertThat(first.start()).isEqualTo("2026-07-20");
        assertThat(first.byProject()).containsExactly(Map.entry(A, 2), Map.entry(B, 1));
        assertThat(out.delivery().typeMix()).extracting("type", "count").containsExactly(
                org.assertj.core.groups.Tuple.tuple("STORY", 1), org.assertj.core.groups.Tuple.tuple("BUG", 1),
                org.assertj.core.groups.Tuple.tuple("TASK", 1));
        assertThat(out.portfolio().getFirst().throughputTrend().getFirst()).isEqualTo(2);
        assertThat(out.previous().itemsDone()).isEqualTo(1);
        assertThat(out.previous().projects()).isNull();
    }

    @Test
    void should_compareConsumptionWithTheSubscription() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(org(new BigDecimal("20"))));
        usage.add(usage("au1", "u-a", "2026-08-04", null, "90.00", "claude"));
        usage.add(usage("au2", "u-b", "2026-08-04", null, "10.00", "gpt"));

        OrganizationDashboardV2Output out = useCase.execute(ORG, Set.of(A, B), 90);

        var sub = out.ai().subscription();
        assertThat(sub.consumptionUsd()).isEqualByComparingTo("100.00");
        // 20 × 3 people × 90/30
        assertThat(sub.subscriptionUsd()).isEqualByComparingTo("180.00");
        // u-a: 90 / 3 months = 30 > 20
        assertThat(sub.usersAboveSubscription()).isEqualTo(1);
        assertThat(out.ai().models()).containsExactly("claude", "gpt");
        assertThat(out.ai().adoption().activeUsers()).isEqualTo(2);
        assertThat(out.ai().adoption().people()).isEqualTo(3);
    }

    @Test
    void should_leaveTheSubscriptionNull_withoutAConfiguredPrice() {
        var sub = useCase.execute(ORG, Set.of(A), 90).ai().subscription();

        assertThat(sub.subscriptionUsd()).isNull();
        assertThat(sub.perUserMonthUsd()).isNull();
        assertThat(sub.usersAboveSubscription()).isNull();
    }

    @Test
    void should_distributeFluencyStages_ofThePeopleOnly() {
        when(fluencyScoreRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(fluency("u-a", 2),
                fluency("u-b", 2), fluency("u-shared", 4), fluency("u-c", 1)));

        var fluency = useCase.execute(ORG, Set.of(A, B), 90).ai().fluency();

        assertThat(fluency).extracting("stage", "count").containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, 0), org.assertj.core.groups.Tuple.tuple(2, 2),
                org.assertj.core.groups.Tuple.tuple(3, 0), org.assertj.core.groups.Tuple.tuple(4, 1));
    }

    @Test
    void should_splitTheAllocatedCostByProject_andReportTheUnclaimedCostApart_toACallerWhoSeesEverything() {
        itemsByProject.get(A).add(done("a1", A, WorkItem.ResolvedType.STORY, 4.0, "2026-08-04T10:00:00Z"));
        itemsByProject.get(B).add(done("b1", B, WorkItem.ResolvedType.STORY, 1.0, "2026-08-04T10:00:00Z"));
        usage.add(usage("au1", "u-a", "2026-08-04", "al1", "10.00", "claude"));
        usage.add(usage("au2", "u-b", "2026-08-05", null, "3.00", "claude"));
        allocations(allocation("al1", "u-a", "2026-08-04", "au1", split("a1", "0.75"), split("b1", "0.25")));

        OrganizationDashboardV2Output out = useCase.execute(ORG, Set.of(A, B, C), 90);

        assertThat(out.ai().costByProject()).extracting(ProjectCost::costUsd).usingComparatorForType(
                BigDecimal::compareTo, BigDecimal.class).containsExactly(new BigDecimal("7.50"), new BigDecimal("2.50"),
                BigDecimal.ZERO, new BigDecimal("3.00"));
        assertThat(out.kpis().aiCostUsd()).as("allocated to the visible projects").isEqualByComparingTo("10.00");
        assertThat(out.ai().subscription().consumptionUsd()).as("the people's whole usage").isEqualByComparingTo("13.00");
        assertThat(out.portfolio().getFirst().aiCostPerSp()).isEqualByComparingTo("1.875");
        assertThat(out.ai().costByProject().getLast().name()).isEqualTo("Não alocado a work items");
        assertThat(out.integrations().coverage().aiDaysAllocatedPct()).isEqualTo(50.0);
    }

    /**
     * Privacy of a partial caller (coordinator's decision, orquestrador option A): u-shared works on A
     * (visible) and C (hidden). The partial caller's aiCostUsd is what was allocated to A and B, the
     * unallocated row is null, so the whole usage minus aiCostUsd only gives "hidden + unallocated"
     * mixed — nothing isolates C. A caller who sees everything keeps the previous answer.
     */
    @Test
    void should_neverIsolateTheCostOfHiddenProjects_forAPartialCaller() {
        itemsByProject.get(A).add(done("a1", A, WorkItem.ResolvedType.STORY, 2.0, "2026-08-04T10:00:00Z"));
        itemsByProject.get(C).add(done("c1", C, WorkItem.ResolvedType.STORY, 2.0, "2026-08-04T10:00:00Z"));
        usage.add(usage("au1", "u-shared", "2026-08-04", "al1", "10.00", "claude"));
        usage.add(usage("au2", "u-shared", "2026-08-05", "al2", "40.00", "claude"));
        usage.add(usage("au3", "u-shared", "2026-08-06", null, "5.00", "claude"));
        usage.add(usage("au0", "u-shared", "2026-05-06", "al0", "8.00", "claude"));
        allocations(allocation("al1", "u-shared", "2026-08-04", "au1", split("a1", "1.0")),
                allocation("al2", "u-shared", "2026-08-05", "au2", split("c1", "1.0")),
                allocation("al0", "u-shared", "2026-05-06", "au0", split("a1", "1.0")));

        OrganizationDashboardV2Output partial = useCase.execute(ORG, Set.of(A, B), 90);

        assertThat(partial.kpis().aiCostUsd()).isEqualByComparingTo("10.00");
        assertThat(partial.previous().aiCostUsd()).as("previous period, allocated to A").isEqualByComparingTo("8.00");
        assertThat(partial.kpis().aiCostPerSp()).isEqualByComparingTo("5.00");
        assertThat(partial.ai().costByProject().getLast().projectId()).isNull();
        assertThat(partial.ai().costByProject().getLast().costUsd()).isNull();
        BigDecimal visibleRows = partial.ai().costByProject().stream().map(ProjectCost::costUsd)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(partial.kpis().aiCostUsd()).isEqualByComparingTo(visibleRows);
        assertThat(partial.ai().subscription().consumptionUsd().subtract(partial.kpis().aiCostUsd()))
                .as("hidden (40) and unallocated (5) only mixed").isEqualByComparingTo("45.00");

        OrganizationDashboardV2Output full = useCase.execute(ORG, Set.of(A, B, C), 90);

        assertThat(full.kpis().aiCostUsd()).as("allocated to A and C").isEqualByComparingTo("50.00");
        assertThat(full.previous().aiCostUsd()).isEqualByComparingTo("8.00");
        assertThat(full.ai().costByProject()).extracting(ProjectCost::projectId).containsExactly(A, B, C, null);
        assertThat(full.ai().costByProject().getLast().costUsd()).isEqualByComparingTo("5.00");
    }

    @Test
    void should_countSyncOutcomesPerDay_ofTheVisibleProjectsAndTheOrganization() {
        List<IntegrationSyncLog> logs = List.of(
                log("l1", A, SyncOperation.SYNC_WORK_ITEMS, SyncStatus.SUCCESS, "2026-10-10T08:00:00Z"),
                log("l2", null, SyncOperation.SYNC_SPRINTS, SyncStatus.FAILED, "2026-10-10T09:00:00Z"),
                log("l3", C, SyncOperation.SYNC_WORK_ITEMS, SyncStatus.FAILED, "2026-10-10T09:00:00Z"),
                log("l4", A, SyncOperation.PROJECT_REFRESH, SyncStatus.PARTIAL, "2026-10-10T09:00:00Z"));
        when(syncLogRepository.findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(eq(ORG), eq(Integration.AZURE_DEVOPS), any(),
                any())).thenAnswer(inv -> {
                    Collection<SyncOperation> operations = inv.getArgument(2);
                    return logs.stream().filter(l -> operations.contains(l.getOperation())).toList();
                });

        List<SyncOutcomeDay> days = useCase.execute(ORG, Set.of(A), 90).integrations().syncByDay();

        assertThat(days).hasSize(14);
        assertThat(days.get(12)).isEqualTo(new SyncOutcomeDay("2026-10-10", "10/10", 1, 0, 1));
    }

    /** Coordinator's decision: aiCostUsd means "allocated to the visible projects" for every caller. */
    @Test
    void should_answerTheSameAiCost_toAPartialAndAFullCaller_whenTheCostWentToTheProjectsBothSee() {
        itemsByProject.get(A).add(done("a1", A, WorkItem.ResolvedType.STORY, 2.0, "2026-08-04T10:00:00Z"));
        usage.add(usage("au1", "u-shared", "2026-08-04", "al1", "10.00", "claude"));
        usage.add(usage("au3", "u-shared", "2026-08-06", null, "5.00", "claude"));
        usage.add(usage("au0", "u-shared", "2026-05-06", "al0", "8.00", "claude"));
        allocations(allocation("al1", "u-shared", "2026-08-04", "au1", split("a1", "1.0")),
                allocation("al0", "u-shared", "2026-05-06", "au0", split("a1", "1.0")));

        OrganizationDashboardV2Output partial = useCase.execute(ORG, Set.of(A, B), 90);
        OrganizationDashboardV2Output full = useCase.execute(ORG, Set.of(A, B, C), 90);

        assertThat(partial.kpis().aiCostUsd()).isEqualByComparingTo("10.00").isEqualByComparingTo(full.kpis().aiCostUsd());
        assertThat(partial.previous().aiCostUsd()).isEqualByComparingTo("8.00")
                .isEqualByComparingTo(full.previous().aiCostUsd());
        assertThat(partial.kpis().aiCostPerSp()).isEqualByComparingTo(full.kpis().aiCostPerSp());
        assertThat(partial.ai().costByProject().getLast().costUsd()).as("partial: unallocated hidden").isNull();
        assertThat(full.ai().costByProject().getLast().costUsd()).as("full: unallocated shown").isEqualByComparingTo("5.00");
    }

    @Test
    void should_carryOnlyTheErrorCodeOfTheConnections_forEveryCaller() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(
                new andromeda.sei.ms.feature.azure.domain.AzureConnection("c1", ORG, "https://dev.azure.com/x", "acc",
                        "ref", NOW, "u-a", andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus.ERROR, NOW, 2,
                        "AZURE_RATE", "Rate limit message", true, NOW, NOW, 0L,
                        andromeda.sei.ms.feature.azure.domain.AzureAuthMethod.PAT, "pat")));

        for (Set<String> visible : List.of(Set.of(A), Set.of(A, B, C))) {
            var azure = useCase.execute(ORG, visible, 90).integrations().azure();
            assertThat(azure.status()).isEqualTo("ERROR");
            assertThat(azure.lastError().code()).isEqualTo("AZURE_RATE");
            assertThat(azure.toString()).doesNotContain("Rate limit message");
        }
    }

    @Test
    void should_reportCoverageOverThePeople_andTheConnectorTokensOfTheOrganization() {
        when(connectorTokenRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                new ConnectorToken("t1", ORG, "u-a", "pc", "hash", true, 0L, NOW, NOW),
                new ConnectorToken("t2", ORG, "u-c", "pc", "hash", true, 0L, NOW, NOW)));
        when(azureBindingRepository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of(
                new AzureProjectBinding("ab", ORG, A, "az-1", "Orion Azure", true, NOW, null, true, NOW, NOW, 0L)));

        var integrations = useCase.execute(ORG, Set.of(A, B), 90).integrations();

        assertThat(integrations.coverage().azureLinkedPct()).isEqualTo(33.33);
        assertThat(integrations.coverage().githubLinkedPct()).isEqualTo(66.67);
        assertThat(integrations.coverage().aiConnectedPct()).isEqualTo(33.33);
        assertThat(integrations.aiConnector().activeTokens()).as("partial caller: the people's tokens only").isEqualTo(1);
        assertThat(useCase.execute(ORG, Set.of(A, B, C), 90).integrations().aiConnector().activeTokens())
                .as("a caller who sees everything: the organization's tokens").isEqualTo(2);
        assertThat(integrations.azure().status()).isEqualTo("DISCONNECTED");
        assertThat(integrations.projects().getFirst().azureProject()).isEqualTo("Orion Azure");
    }

    private void allocations(AiUsageAllocation... allocations) {
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(eq(ORG), anyCollection(),
                any(), any())).thenReturn(List.of(allocations));
    }

    private static AiUsageAllocation allocation(String id, String userId, String day, String usageId,
                                                AllocationSplit... splits) {
        return new AiUsageAllocation(id, ORG, userId, LocalDate.parse(day), List.of(usageId), List.of(splits),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
    }

    private static AllocationSplit split(String workItemId, String percentage) {
        return new AllocationSplit(workItemId, "x", new BigDecimal(percentage));
    }

    private void bind(String repositoryId, String projectId) {
        VcsRepoBinding binding = new VcsRepoBinding("b-" + repositoryId, ORG, projectId, repositoryId,
                VcsProvider.GITHUB, true);
        when(bindingRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(binding));
        when(repositoryRepository.findAllByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(List.of(
                new VcsRepository(repositoryId, ORG, VcsProvider.GITHUB, "ext", "orbital/" + repositoryId)));
    }

    private static Project project(String id, String name, String... members) {
        return new Project(id, ORG, name, id, null, true, List.of(members), 0L, NOW, NOW);
    }

    private static User user(String id, String azureId, String githubId) {
        return new User(id, ORG, id + "@x.io", null, id, "role", null, githubId, azureId, null, null, true, true,
                0L, NOW, NOW);
    }

    private static WorkItem item(String id, String projectId, WorkItem.ResolvedType type, WorkItem.ResolvedStatus status,
                                 Double effort) {
        WorkItem item = WorkItemFixture.item(id, type, status, effort, null);
        item.setProjectId(projectId);
        return item;
    }

    private static WorkItem done(String id, String projectId, WorkItem.ResolvedType type, Double effort,
                                 String completedAt) {
        WorkItem item = item(id, projectId, type, WorkItem.ResolvedStatus.DONE, effort);
        item.setCompletedAt(Instant.parse(completedAt));
        item.setCycleTimeHours(10.0);
        item.setLeadTimeHours(20.0);
        return item;
    }

    private static WorkItem inSprint(WorkItem item, String sprintId) {
        item.setSprintId(sprintId);
        return item;
    }

    private static Sprint sprint(String id, String projectId, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, projectId, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", id,
                "\\X\\" + id, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static AIUsage usage(String id, String userId, String day, String allocationId, String cost, String model) {
        return new AIUsage(id, ORG, userId, "gh", "ds", LocalDate.parse(day), model, "w", "w", "m", "m",
                100, 50, 2, "vscode", allocationId, new BigDecimal(cost), NOW, true, 0L, NOW, NOW);
    }

    private static VcsPullRequest pr(String id, String repositoryId, String createdAt, String mergedAt) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, repositoryId, VcsProvider.GITHUB, "ext-" + id,
                Integer.parseInt(id.replaceAll("\\D", "")), "PR " + id,
                mergedAt == null ? PullRequestStatus.OPEN : PullRequestStatus.MERGED);
        pr.setCreatedAt(Instant.parse(createdAt));
        pr.setMergedAt(mergedAt == null ? null : Instant.parse(mergedAt));
        pr.setActive(true);
        return pr;
    }

    private static Organization org(BigDecimal seatCost) {
        return new Organization(ORG, "Acme", "acme", null, null, List.of(), List.of(), seatCost, true, 0L, NOW, NOW);
    }

    private static FluencyScore fluency(String userId, int stage) {
        return new FluencyScore("f-" + userId, ORG, userId, stage, "x", "2026-10-01", List.of(), true, 0L, NOW, NOW);
    }

    private static IntegrationSyncLog log(String id, String projectId, SyncOperation operation, SyncStatus status,
                                          String startedAt) {
        return new IntegrationSyncLog(id, ORG, Integration.AZURE_DEVOPS, operation, status, SyncTrigger.SCHEDULED,
                projectId, Instant.parse(startedAt), null, 1000L, 1, 0, null, null);
    }
}

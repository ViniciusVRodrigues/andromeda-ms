package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.AiUsageSummaries;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ThroughputRow;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.dashboard.domain.exception.DashboardPeriodInvalidException;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.7/1.10 — project overview v2, table tests ported from the preview's
 * {@code buildProject}: throughput by type and bucket, committed × delivered, consumption ×
 * subscription, PR flow, repository health, the allocated AI cost per sprint and an empty period.
 * Now = Sunday 2026-10-11 12:00Z; 90 days = 2026-07-14 → 2026-10-11 (weekly buckets from 20/07),
 * previous = 2026-04-15 → 2026-07-13.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetProjectOverviewV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final String ME = "user-a";
    private static final String OTHER = "user-b";
    private static final String THIRD = "user-c";
    private static final String REPO = "repo-1";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private VcsCommitRepository commitRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;

    private final List<WorkItem> items = new ArrayList<>();
    private GetProjectOverviewV2UseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProjectOverviewV2UseCase(projectRepository, workItemRepository, sprintRepository,
                new ProjectVcsActivity(vcsReadPort, commitRepository), vcsReadPort, new EffortBearingLevelResolver(),
                new ProjectAiCostCalculator(allocationRepository, aiUsageRepository), aiUsageRepository,
                allocationRepository, new DeliveryMetricsService(vcsReadPort, workItemRepository, organizationRepository),
                new DashboardPeopleDirectory(userRepository, connectorTokenRepository), organizationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of(ME, OTHER, THIRD), 0L, NOW, NOW)));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(items);
        // the AI chain starts from the ids of the active items (a lean read of the same items)
        when(workItemRepository.findActiveIdsByOrganizationIdAndProjectId(ORG, PROJECT)).thenAnswer(inv -> items.stream()
                .filter(WorkItem::isActive).map(WorkItem::getId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void should_throwProjectNotFound_when_projectIsNotOfTheOrganization() {
        assertThatThrownBy(() -> useCase.execute("org-2", PROJECT, 90)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_rejectAPeriodOutsideTheAllowedSet() {
        assertThatThrownBy(() -> useCase.execute(ORG, PROJECT, 45)).isInstanceOf(DashboardPeriodInvalidException.class);
    }

    @Test
    void should_countThroughputByTypeAndBucket_andCompareWithThePreviousPeriod() {
        items.addAll(List.of(
                done("s1", WorkItem.ResolvedType.STORY, 3.0, "2026-07-21T10:00:00Z"),
                done("b1", WorkItem.ResolvedType.BUG, 2.0, "2026-07-22T10:00:00Z"),
                done("t1", WorkItem.ResolvedType.TASK, 5.0, "2026-08-04T10:00:00Z"),
                done("f1", WorkItem.ResolvedType.FEATURE, 13.0, "2026-07-21T10:00:00Z"),
                done("s0", WorkItem.ResolvedType.STORY, 1.0, "2026-05-01T10:00:00Z"),
                done("s00", WorkItem.ResolvedType.STORY, 1.0, "2026-07-13T23:00:00Z")));

        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.period().granularity()).isEqualTo("semana");
        assertThat(out.period().start()).isEqualTo("2026-07-14");
        assertThat(out.throughput()).hasSize(12);
        assertThat(out.throughput().get(0)).isEqualTo(new ThroughputRow("2026-07-20", "20/07", 1, 1, 0));
        assertThat(out.throughput().get(2)).isEqualTo(new ThroughputRow("2026-08-03", "03/08", 0, 0, 1));
        assertThat(out.kpis().throughput()).isEqualTo(3);
        assertThat(out.previous().throughput()).isEqualTo(2);
        assertThat(out.kpis().bugSharePct()).isEqualTo(33.33);
        // story + bug; the task's effort rolls into no story (STORY_AND_TASK) and the feature is not a unit
        assertThat(out.kpis().spDelivered()).isEqualTo(5.0);
        assertThat(out.quality().get(0).bugSharePct()).isEqualTo(50.0);
        assertThat(out.quality().get(1).bugSharePct()).isNull();
    }

    @Test
    void should_compareCommittedWithDelivered_perPastSprintEndedInThePeriod_withAllocatedCostPerSp() {
        sprints(sprint("s0", "Sprint 0", Sprint.TimeFrame.PAST, "2026-06-15", "2026-06-26"),
                sprint("s1", "Sprint 1", Sprint.TimeFrame.PAST, "2026-08-03", "2026-08-14"),
                sprint("s2", "Sprint 2", Sprint.TimeFrame.PAST, "2026-08-17", "2026-08-28"),
                sprint("s3", "Sprint 3", Sprint.TimeFrame.CURRENT, "2026-10-05", "2026-10-16"));
        items.addAll(List.of(inSprint(done("a1", WorkItem.ResolvedType.STORY, 5.0, "2026-08-10T10:00:00Z"), "s1"),
                inSprint(WorkItemFixture.item("a2", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO, 3.0, null), "s1"),
                inSprint(done("b1", WorkItem.ResolvedType.STORY, 4.0, "2026-08-20T10:00:00Z"), "s2"),
                inSprint(done("c1", WorkItem.ResolvedType.STORY, 2.0, "2026-06-20T10:00:00Z"), "s0")));
        AIUsage usage = usage("u1", ME, "2026-08-05", "al1", "10.00", "claude");
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(eq(ORG), anyCollection(),
                any(), any()))
                .thenReturn(List.of(allocation("al1", ME, "2026-08-05", "u1", "a1")));
        when(aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(List.of(usage));

        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.sprints()).extracting(ProjectOverviewV2Output.SprintRow::label).containsExactly("S1", "S2");
        assertThat(out.sprints().get(0).spPlanned()).isEqualTo(8.0);
        assertThat(out.sprints().get(0).spDelivered()).isEqualTo(5.0);
        assertThat(out.sprints().get(0).predictabilityPct()).isEqualTo(62.5);
        assertThat(out.sprints().get(0).aiCostUsd()).isEqualByComparingTo("10");
        assertThat(out.sprints().get(0).aiCostPerSp()).isEqualByComparingTo("2");
        assertThat(out.sprints().get(1).aiCostPerSp()).isEqualByComparingTo("0");
        assertThat(out.kpis().predictabilityPct()).isEqualTo(81.25);
        assertThat(out.previous().predictabilityPct()).isEqualTo(100.0);
        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("10");
        assertThat(out.kpis().aiCostPerSp()).isEqualByComparingTo("1.111111");
        assertThat(out.ai().models()).containsExactly("claude");
        assertThat(out.ai().costByBucket().get(2).byModel()).containsEntry("claude", new BigDecimal("10.00"));
        assertThat(out.ai().costByBucket().get(0).byModel().get("claude")).isEqualByComparingTo("0");
        assertThat(out.project().team()).isEqualTo("Core");
        assertThat(out.dataQuality().itemsWithEstimatePct()).isEqualTo(100.0);
    }

    @Test
    void should_compareMembersConsumption_withTheSubscriptionOfThePeriod() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(org(new BigDecimal("20"))));
        when(aiUsageRepository.summarizeByUserAndDay(eq(ORG), anyCollection(),
                eq(LocalDate.parse("2026-07-14")), eq(LocalDate.parse("2026-10-11"))))
                .thenReturn(AiUsageSummaries.of(List.of(usage("u1", ME, "2026-08-01", null, "4.00", "claude"),
                        usage("u2", OTHER, "2026-08-02", null, "6.00", "gpt-5"))));

        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.ai().subscription().consumptionUsd()).isEqualByComparingTo("10");
        assertThat(out.ai().subscription().subscriptionUsd()).isEqualByComparingTo("180");
        assertThat(out.ai().subscription().perUserMonthUsd()).isEqualByComparingTo("20");
        assertThat(out.ai().adoption()).isEqualTo(new ProjectOverviewV2Output.Adoption(2, 3));
        assertThat(out.dataQuality().aiDaysAllocatedPct()).isEqualTo(0.0);
        // the members' usage is the seat question: none of it is the project's allocated cost
        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("0");
    }

    @Test
    void should_leaveTheSubscriptionNull_when_theOrganizationHasNoSeatCost() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(org(null)));

        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 30);

        assertThat(out.ai().subscription().subscriptionUsd()).isNull();
        assertThat(out.ai().subscription().perUserMonthUsd()).isNull();
    }

    @Test
    void should_countPrFlow_repoHealth_andCommits_perPeriod() {
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(
                new VcsRepository(REPO, ORG, VcsProvider.GITHUB, "ext-repo", "owner/orion")));
        VcsRepoBinding binding = new VcsRepoBinding("bd1", ORG, PROJECT, REPO, VcsProvider.GITHUB, true);
        when(vcsReadPort.bindingsOfProject(ORG, PROJECT)).thenReturn(List.of(binding));
        VcsPullRequest merged = pr("pr1", "2026-07-21T10:00:00Z", "2026-07-23T10:00:00Z", 2.0);
        merged.setCycleTimeHours(48.0);
        merged.setLinkedWorkItemExternalId("ext-x");
        VcsPullRequest open = pr("pr2", "2026-07-22T10:00:00Z", null, 6.0);
        VcsPullRequest old = pr("pr3", "2026-05-01T10:00:00Z", "2026-05-02T10:00:00Z", 1.0);
        // PRs are read from the start of the previous period (plus the open ones), not the whole history
        when(vcsReadPort.pullRequestsTouchedSince(eq(ORG), eq(List.of(REPO)), eq(Instant.parse("2026-04-15T00:00:00Z"))))
                .thenReturn(List.of(merged, open, old));
        // commits are counted in the database per repository and period (2 in the period, 1 before)
        when(commitRepository.countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(ORG, REPO,
                Instant.parse("2026-07-14T00:00:00Z"), Instant.parse("2026-10-12T00:00:00Z"))).thenReturn(2L);
        when(commitRepository.countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(ORG, REPO,
                Instant.parse("2026-04-15T00:00:00Z"), Instant.parse("2026-07-14T00:00:00Z"))).thenReturn(1L);

        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.prFlow().get(0).opened()).isEqualTo(2);
        assertThat(out.prFlow().get(0).merged()).isEqualTo(1);
        assertThat(out.prFlow().get(0).timeToFirstReviewHours()).isEqualTo(new DurationStat(2.0, 6.0, 2));
        assertThat(out.kpis().prsMerged()).isEqualTo(1);
        assertThat(out.previous().prsMerged()).isEqualTo(1);
        assertThat(out.kpis().prCycleTimeHours()).isEqualTo(new DurationStat(48.0, 48.0, 1));
        assertThat(out.kpis().commitsCount()).isEqualTo(2);
        assertThat(out.previous().commitsCount()).isEqualTo(1);
        assertThat(out.previous().stalePrs()).isNull();
        assertThat(out.dataQuality().prsLinkedPct()).isEqualTo(50.0);
        assertThat(out.dora().method()).isEqualTo("VCS_PROXY");
        assertThat(out.dora().deployFrequency().estimated()).isTrue();
        var repo = out.repos().getFirst();
        assertThat(repo.repo()).isEqualTo("owner/orion");
        assertThat(repo.commits()).isEqualTo(2);
        assertThat(repo.prsMerged()).isEqualTo(1);
        assertThat(repo.openPrs()).isEqualTo(1);
        assertThat(repo.syncEnabled()).isTrue();
        assertThat(repo.cfrPct()).isEqualTo(0.0);
        assertThat(out.project().repos()).containsExactly("owner/orion");
    }

    @Test
    void should_countMembersCoverage() {
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                user(ME, "gh-a", "az-a"), user(OTHER, null, "az-b"), user(THIRD, null, null)));
        when(connectorTokenRepository.findActiveByOrganizationIdAndUserId(ORG, ME))
                .thenReturn(List.of(new ConnectorToken("t1", ORG, ME, "laptop", "hash", true, 0L, NOW, NOW)));

        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.people()).isEqualTo(new ProjectOverviewV2Output.People(3, 2, 1, 1));
    }

    @Test
    void should_returnNullsAndEmptyLists_butEveryBucket_when_thePeriodHasNoData() {
        ProjectOverviewV2Output out = useCase.execute(ORG, PROJECT, 180);

        assertThat(out.period().granularity()).isEqualTo("mês");
        assertThat(out.kpis().throughput()).isZero();
        assertThat(out.kpis().spDelivered()).isNull();
        assertThat(out.kpis().predictabilityPct()).isNull();
        assertThat(out.kpis().leadTimeHours()).isEqualTo(DurationStat.empty());
        assertThat(out.kpis().bugSharePct()).isNull();
        assertThat(out.kpis().aiCostPerSp()).isNull();
        assertThat(out.kpis().deployFreqPerWeek()).isNull();
        assertThat(out.dora().method()).isEqualTo("UNAVAILABLE");
        assertThat(out.dora().leadTime().grade()).isNull();
        assertThat(out.throughput()).hasSize(6).allSatisfy(r -> assertThat(r.story() + r.bug() + r.task()).isZero());
        assertThat(out.flow()).hasSize(6).allSatisfy(r -> assertThat(r.cycleTimeHours()).isEqualTo(DurationStat.empty()));
        assertThat(out.quality()).allSatisfy(r -> assertThat(r.bugSharePct()).isNull());
        assertThat(out.prFlow()).hasSize(6).allSatisfy(r -> assertThat(r.timeToFirstReviewHours().n()).isZero());
        assertThat(out.sprints()).isEmpty();
        assertThat(out.repos()).isEmpty();
        assertThat(out.ai().models()).isEmpty();
        assertThat(out.ai().costByBucket()).hasSize(6).allSatisfy(b -> assertThat(b.byModel()).isEmpty());
        assertThat(out.ai().tokensPerSp()).isNull();
        assertThat(out.dataQuality()).isEqualTo(new ProjectOverviewV2Output.DataQuality(null, null, null));
        assertThat(out.project().team()).isNull();
        assertThat(out.asOf()).isEqualTo(NOW);
    }

    private void sprints(Sprint... sprints) {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(sprints));
    }

    private static WorkItem done(String id, WorkItem.ResolvedType type, Double effort, String completedAt) {
        WorkItem item = WorkItemFixture.item(id, type, WorkItem.ResolvedStatus.DONE, effort, null);
        item.setCompletedAt(Instant.parse(completedAt));
        item.setCycleTimeHours(10.0);
        item.setLeadTimeHours(20.0);
        return item;
    }

    private static WorkItem inSprint(WorkItem item, String sprintId) {
        item.setSprintId(sprintId);
        return item;
    }

    private static Sprint sprint(String id, String name, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", name,
                "\\Orion\\" + name, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static AIUsage usage(String id, String userId, String day, String allocationId, String cost, String model) {
        return new AIUsage(id, ORG, userId, "gh", "ds", LocalDate.parse(day), model, "w", "w", "m", "m",
                100, 50, 2, "vscode", allocationId, new BigDecimal(cost), NOW, true, 0L, NOW, NOW);
    }

    private static AiUsageAllocation allocation(String id, String userId, String day, String usageId, String workItemId) {
        return new AiUsageAllocation(id, ORG, userId, LocalDate.parse(day), List.of(usageId),
                List.of(new AllocationSplit(workItemId, "Item", BigDecimal.ONE)),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
    }

    private static Organization org(BigDecimal seatCost) {
        return new Organization(ORG, "Acme", "acme", null, null, List.of(), List.of(), seatCost, true, 0L, NOW, NOW);
    }

    private static User user(String id, String githubId, String azureId) {
        return new User(id, ORG, id + "@x.io", null, id, "role", null, githubId, azureId, null, null, true, true,
                0L, NOW, NOW);
    }

    private static VcsPullRequest pr(String id, String createdAt, String mergedAt, Double ttfr) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, REPO, VcsProvider.GITHUB, "ext-" + id,
                Integer.parseInt(id.replaceAll("\\D", "")), "PR " + id,
                mergedAt == null ? PullRequestStatus.OPEN : PullRequestStatus.MERGED);
        pr.setCreatedAt(Instant.parse(createdAt));
        pr.setMergedAt(mergedAt == null ? null : Instant.parse(mergedAt));
        pr.setTimeToFirstReviewHours(ttfr);
        pr.setActive(true);
        return pr;
    }

    private static VcsCommit commit(String id, String authoredAt) {
        VcsCommit commit = new VcsCommit(id, ORG, REPO, VcsProvider.GITHUB, id);
        commit.setAuthoredAt(Instant.parse(authoredAt));
        return commit;
    }
}

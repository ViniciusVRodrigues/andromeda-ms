package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.AiUsageSummaries;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTeamDashboardUseCaseTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private UserRepository userRepository;
    @Mock private VcsReadPort vcsReadPort;

    private GetTeamDashboardUseCase useCase;

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String SPRINT_ID = "sprint-1";
    private static final Instant NOW = Instant.now();
    private static final Instant SPRINT_START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant SPRINT_END = Instant.parse("2026-06-14T00:00:00Z");

    @BeforeEach
    void setUp() {
        useCase = new GetTeamDashboardUseCase(sprintRepository, workItemRepository, new EffortBearingLevelResolver(),
                aiUsageRepository, userRepository, vcsReadPort,
                new ProjectAiCostCalculator(allocationRepository, aiUsageRepository));
        lenient().when(userRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
        lenient().when(aiUsageRepository.findTotalCostByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of());
        lenient().when(vcsReadPort.repositoriesOfProject(any(), any())).thenReturn(List.of());
        lenient().when(workItemRepository.findAllByOrganizationIdAndProjectId(any(), any())).thenReturn(List.of());
    }

    // ── stale PRs ──────────────────────────────────────────────────────────

    @Test
    void should_includeStalePr_when_openMoreThan24hWithNoReview() {
        givenRepo();
        VcsPullRequest stalePr = pullRequest("pr-1", NOW.minusSeconds(48 * 3600), null);
        when(vcsReadPort.openPullRequests(ORG, List.of("mongo-repo-1"))).thenReturn(List.of(stalePr));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).hasSize(1);
        assertThat(output.stalePrs().get(0).prId()).isEqualTo("pr-1");
        assertThat(output.stalePrs().get(0).hoursOpen()).isGreaterThanOrEqualTo(48.0);
    }

    @Test
    void should_excludeReviewedAndDraftPrs_when_listingStale() {
        givenRepo();
        VcsPullRequest reviewed = pullRequest("pr-2", NOW.minusSeconds(48 * 3600), 10.0);
        VcsPullRequest draft = pullRequest("pr-3", NOW.minusSeconds(48 * 3600), null);
        draft.setDraft(true);
        when(vcsReadPort.openPullRequests(ORG, List.of("mongo-repo-1"))).thenReturn(List.of(reviewed, draft));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).isEmpty();
    }

    @Test
    void should_returnEmptyStalePrsAndEmptyReview_when_projectHasNoBoundRepos() {
        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).isEmpty();
        assertThat(output.review().eligiblePrs()).isZero();
        assertThat(output.review().slaHours()).isEqualTo(24);
    }

    @Test
    void should_resolveAuthorName_viaLinkedAuthorUserId_beforeFallingBackToLogin() {
        givenRepo();
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(user("user-42", "Linked Dev", "d@x")));
        VcsPullRequest linked = pullRequest("pr-1", NOW.minusSeconds(48 * 3600), null);
        linked.setAuthorUserId("user-42");
        VcsPullRequest unlinked = pullRequest("pr-2", NOW.minusSeconds(50 * 3600), null);
        when(vcsReadPort.openPullRequests(ORG, List.of("mongo-repo-1"))).thenReturn(List.of(linked, unlinked));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).extracting(TeamDashboardOutput.StalePrSummary::authorName)
                .containsExactly("gh-author", "Linked Dev");
        assertThat(output.stalePrs()).extracting(TeamDashboardOutput.StalePrSummary::authorLogin)
                .containsExactly("gh-author", "gh-author");
        assertThat(output.stalePrs()).extracting(TeamDashboardOutput.StalePrSummary::provider)
                .containsOnly("GITHUB");
    }

    @Test
    void should_notFail_when_userHasNoNameNorEmailOrIsDuplicated() {
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                user("u1", null, null), user("u1", "Dup", null), user("u2", " ", "mail@x")));
        givenSprint(sprint(SPRINT_ID, SPRINT_START, SPRINT_END));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(story("wi-1", "u1", WorkItem.ResolvedStatus.DONE, 3.0),
                        story("wi-2", "u2", WorkItem.ResolvedStatus.DONE, 2.0)));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        assertThat(output.members()).extracting(TeamDashboardOutput.MemberMetrics::name)
                .containsExactly("u1", "mail@x");
    }

    // ── velocity (single definition) and percentiles ─────────────────────

    @Test
    void should_countTaskEffortInVelocity_when_teamEstimatesOnTasks() {
        givenSprint(sprint(SPRINT_ID, SPRINT_START, SPRINT_END));
        WorkItem storyNoEffort = story("s1", "user-1", WorkItem.ResolvedStatus.DONE, null);
        WorkItem t1 = task("t1", "user-1", WorkItem.ResolvedStatus.DONE, 3.0, storyNoEffort);
        WorkItem t2 = task("t2", "user-2", WorkItem.ResolvedStatus.DONE, 5.0, storyNoEffort);
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(storyNoEffort, t1, t2));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        assertThat(output.teamVelocity()).isEqualTo(8.0);
        assertThat(output.members()).extracting(TeamDashboardOutput.MemberMetrics::storyPointsDelivered)
                .containsExactly(5.0, 3.0);
    }

    @Test
    void should_includeFeatureTypedWorkItem_inMemberVelocity_notDropIt() {
        givenSprint(sprint(SPRINT_ID, SPRINT_START, SPRINT_END));
        WorkItem feature = story("wi-feat", "user-1", WorkItem.ResolvedStatus.DONE, 8.0);
        feature.setResolvedType(WorkItem.ResolvedType.FEATURE);
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(feature));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        assertThat(output.members()).hasSize(1);
        assertThat(output.members().get(0).storyPointsDelivered()).isEqualTo(8.0);
        assertThat(output.teamVelocity()).isEqualTo(8.0);
    }

    @Test
    void should_reportPercentiles_when_cycleTimesAreSkewed() {
        givenSprint(sprint(SPRINT_ID, SPRINT_START, SPRINT_END));
        double[] cycles = {2, 3, 3, 4, 100};
        List<WorkItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < cycles.length; i++) {
            WorkItem item = story("wi-" + i, "user-1", WorkItem.ResolvedStatus.DONE, 1.0);
            item.setCycleTimeHours(cycles[i]);
            items.add(item);
        }
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(items);

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        assertThat(output.cycleTime()).isEqualTo(new DurationStat(3.0, 100.0, 5));
        assertThat(output.members().get(0).cycleTime()).isEqualTo(new DurationStat(3.0, 100.0, 5));
        assertThat(output.avgCycleTimeHours()).isEqualTo(22.4);
        assertThat(output.leadTime().n()).isZero();
    }

    @Test
    void should_carrySpPlannedAndTotalTokens_inVelocityTrend() {
        Sprint sprint = sprint(SPRINT_ID, NOW.minusSeconds(864000), NOW.plusSeconds(86400));
        when(sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(Optional.empty());
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(
                        story("wi-1", "user-1", WorkItem.ResolvedStatus.DONE, 13.0),
                        story("wi-2", "user-1", WorkItem.ResolvedStatus.DONE, 9.0),
                        story("wi-3", "user-1", WorkItem.ResolvedStatus.IN_PROGRESS, 8.0)));
        when(aiUsageRepository.findTotalCostByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of("user-1", new BigDecimal("0.72")));
        when(aiUsageRepository.findTotalTokensByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of("user-1", 9000L));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        TeamDashboardOutput.VelocityPoint point = output.velocityTrend().get(0);
        assertThat(point.spPlanned()).isEqualTo(30.0);
        assertThat(point.storyPoints()).isEqualTo(22.0);
        assertThat(point.totalTokens()).isEqualTo(9000L);
        assertThat(point.aiCostUsd()).isEqualByComparingTo("0.72");
    }

    @Test
    void should_nullTokensAndCost_inVelocityTrend_whenSprintHasNoDates() {
        Sprint sprint = sprint(SPRINT_ID, null, null);
        sprint.setTimeFrame(Sprint.TimeFrame.PAST);
        when(sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(Optional.empty());
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(story("wi-1", "user-1", WorkItem.ResolvedStatus.DONE, 5.0)));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        TeamDashboardOutput.VelocityPoint point = output.velocityTrend().get(0);
        assertThat(point.spPlanned()).isEqualTo(5.0);
        assertThat(point.storyPoints()).isEqualTo(5.0);
        assertThat(point.totalTokens()).isNull();
        assertThat(point.aiCostUsd()).isNull();
    }

    // ── shared iteration (one sprint per team) ─────────────────────────────

    @Test
    void should_readCanonicalTwinItems_when_anotherTeamsTwinIsRequested() {
        Sprint canonical = sprint(SPRINT_ID, SPRINT_START, SPRINT_END);
        Sprint twin = twinOf(canonical, "sprint-team-b", "team-b", NOW.plusSeconds(60));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("sprint-team-b", ORG)).thenReturn(Optional.of(twin));
        when(sprintRepository.findAllByOrganizationIdAndProjectIdAndPath(ORG, PROJECT, "/path"))
                .thenReturn(List.of(twin, canonical));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(story("wi-1", "user-1", WorkItem.ResolvedStatus.DONE, 5.0)));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, "sprint-team-b", null, null);

        assertThat(output.sprintId()).isEqualTo(SPRINT_ID);
        assertThat(output.teamVelocity()).isEqualTo(5.0);
    }

    @Test
    void should_listSharedIterationOnce_andCountItsItemsOnce_inVelocityTrend() {
        Sprint canonical = sprint(SPRINT_ID, NOW.minusSeconds(864000), NOW.plusSeconds(86400));
        Sprint twin = twinOf(canonical, "sprint-team-b", "team-b", NOW.plusSeconds(60));
        when(sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(Optional.empty());
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(twin, canonical));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(story("wi-1", "user-1", WorkItem.ResolvedStatus.DONE, 8.0)));
        lenient().when(aiUsageRepository.findTotalTokensByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of());

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.velocityTrend()).extracting(TeamDashboardOutput.VelocityPoint::sprintId)
                .containsExactly(SPRINT_ID);
        assertThat(output.velocityTrend().get(0).storyPoints()).isEqualTo(8.0);
    }

    // ── sprint scoping ───────────────────────────────────────────────────

    @Test
    void should_throwSprintNotFound_when_sprintBelongsToAnotherOrganization() {
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("foreign-sprint", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, "auth-user", PROJECT, "foreign-sprint", null, null))
                .isInstanceOf(SprintNotFoundException.class)
                .extracting("code").isEqualTo("SPRINT_NOT_FOUND");
    }

    @Test
    void should_throwSprintNotFound_when_sprintBelongsToAnotherProject() {
        Sprint other = sprint(SPRINT_ID, SPRINT_START, SPRINT_END);
        other.setProjectId("other-project");
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue(SPRINT_ID, ORG)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null))
                .isInstanceOf(SprintNotFoundException.class);
    }

    // ── AI cost scoped to the project ─────────────────────────────────────

    @Test
    void should_chargeOnlyProjectAllocatedCost_when_memberWorksOnTwoProjects() {
        givenSprint(sprint(SPRINT_ID, SPRINT_START, SPRINT_END));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(story("wi-a", "user-1", WorkItem.ResolvedStatus.DONE, 3.0)));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(story("wi-a", "user-1", WorkItem.ResolvedStatus.DONE, 3.0)));
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-14");
        // the query is project-scoped now; the other project's allocation stays in the stub to prove the share filter
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(
                eq(ORG), any(), eq(from), eq(to))).thenReturn(List.of(
                allocation("user-1", List.of("u-a"), "wi-a"),
                allocation("user-1", List.of("u-b"), "wi-other-project")));
        when(aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(eq(ORG), any())).thenReturn(List.of(
                usage("u-a", "user-1", "10.00", "alloc")));
        when(aiUsageRepository.summarizeByUserAndDay(eq(ORG), any(), eq(from), eq(to)))
                .thenReturn(AiUsageSummaries.of(List.of(usage("u-a", "user-1", "10.00", "alloc"),
                        usage("u-c", "user-1", "5.00", null))));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        assertThat(output.teamAiCostUsd()).isEqualByComparingTo("10.00");
        assertThat(output.members().get(0).aiCostUsd()).isEqualByComparingTo("10.00");
        assertThat(output.unallocatedAiCostUsd()).isEqualByComparingTo("5.00");
        assertThat(output.members().get(0).unallocatedAiCostUsd()).isEqualByComparingTo("5.00");
    }

    @Test
    void should_endCostWindowAtSprintEnd_when_pastSprintRequested() {
        givenSprint(sprint(SPRINT_ID, SPRINT_START, SPRINT_END));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(story("wi-a", "user-1", WorkItem.ResolvedStatus.DONE, 3.0)));

        useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        verify(aiUsageRepository).summarizeByUserAndDay(eq(ORG), any(),
                eq(LocalDate.parse("2026-06-01")), eq(LocalDate.parse("2026-06-14")));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void givenRepo() {
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT))
                .thenReturn(List.of(VcsLegacyFixtures.repository("mongo-repo-1", ORG, "ext", "octo", "hello", "octo/hello",
                        NOW, true, NOW, NOW, 1L)));
    }

    private void givenSprint(Sprint sprint) {
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue(sprint.getId(), ORG)).thenReturn(Optional.of(sprint));
        lenient().when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());
    }

    private Sprint sprint(String id, Instant startDate, Instant endDate) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "ext-" + id, "team-1", "Team A", "Sprint 25", "/path",
                startDate, endDate, Sprint.TimeFrame.CURRENT, true, 1L, NOW, NOW);
    }

    private Sprint twinOf(Sprint canonical, String id, String teamId, Instant createdAt) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, canonical.getExternalId(), teamId, "Team B",
                canonical.getName(), canonical.getPath(), canonical.getStartDate(), canonical.getEndDate(),
                canonical.getTimeFrame(), true, 1L, createdAt, createdAt);
    }

    private VcsPullRequest pullRequest(String id, Instant createdAt, Double timeToFirstReviewHours) {
        return VcsLegacyFixtures.pullRequest(id, ORG, "mongo-repo-1", "ext-" + id, 1, "PR " + id, PullRequestStatus.OPEN,
                "gh-author", null, createdAt, null, null, createdAt, null, timeToFirstReviewHours, true, NOW, 1L);
    }

    private User user(String id, String name, String email) {
        return new User(id, ORG, email, "hash", name, null, null, null, null, null, null, true, true, 1L, NOW, NOW);
    }

    private WorkItem story(String id, String userId, WorkItem.ResolvedStatus status, Double effort) {
        WorkItem item = andromeda.sei.ms.feature.workitem.domain.WorkItemFixture.item(id,
                WorkItem.ResolvedType.STORY, status, effort, null);
        item.setSprintId(SPRINT_ID);
        item.setAssigneeUserId(userId);
        if (status == WorkItem.ResolvedStatus.DONE) item.setCompletedAt(NOW);
        return item;
    }

    private WorkItem task(String id, String userId, WorkItem.ResolvedStatus status, Double effort, WorkItem parent) {
        WorkItem item = story(id, userId, status, effort);
        item.setResolvedType(WorkItem.ResolvedType.TASK);
        item.setParentExternalId(parent.getExternalId());
        return item;
    }

    private AiUsageAllocation allocation(String userId, List<String> usageIds, String workItemId) {
        return new AiUsageAllocation("alloc-" + workItemId, ORG, userId, LocalDate.parse("2026-06-02"), usageIds,
                List.of(new AllocationSplit(workItemId, "t", BigDecimal.ONE)), AiUsageAllocation.Status.VALIDATED,
                AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
    }

    private AIUsage usage(String id, String userId, String cost, String allocationId) {
        return new AIUsage(id, ORG, userId, "gh", "ds", LocalDate.parse("2026-06-02"), "gpt", "w", "w", "m", "m",
                10, 10, 1, "vscode", allocationId, new BigDecimal(cost), NOW, true, 0L, NOW, NOW);
    }
}

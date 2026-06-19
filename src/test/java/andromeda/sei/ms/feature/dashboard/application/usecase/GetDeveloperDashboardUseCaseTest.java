package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.dto.AiSprintSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDeveloperDashboardUseCaseTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private OrganizationRepository organizationRepository;

    private GetDeveloperDashboardUseCase useCase;

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final String PROJECT = "proj-1";
    private static final String SPRINT_ID = "sprint-1";

    // Sprint: June 1 – June 10 (10 days)
    private static final LocalDate SPRINT_START = LocalDate.of(2026, 6, 1);
    private static final LocalDate SPRINT_END = LocalDate.of(2026, 6, 10);

    @BeforeEach
    void setUp() {
        useCase = new GetDeveloperDashboardUseCase(
                sprintRepository, workItemRepository, aiUsageRepository, allocationRepository, organizationRepository);
        // Default: no validated allocations. Individual tests override as needed.
        lenient().when(allocationRepository.findValidatedByOrgUserAndWorkItemId(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(organizationRepository.findById(any())).thenReturn(Optional.empty());
    }

    // ── Task 2.5: null metrics ─────────────────────────────────────────────────

    @Test
    void should_return_null_avgCycleTime_when_all_done_items_have_null_cycleTime() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        WorkItem doneItem = workItem("wi-1", WorkItem.ResolvedStatus.DONE, null, null, null);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of(doneItem));

        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        assertThat(output.avgCycleTimeHours()).isNull();
        assertThat(output.avgLeadTimeHours()).isNull();
        assertThat(output.storyPointsDelivered()).isNull();
    }

    // ── Task 3.6a: burndown series ─────────────────────────────────────────────

    @Test
    void should_compute_burndownSeries_with_correct_remainingSp_and_idealSp() {
        // Sprint: June 1 – June 10, total 30 SP
        // Item 1 (10 SP): completed June 3
        // Item 2 (5 SP): completed June 6
        // totalSp = 15, remaining after day 3 = 5, after day 6 = 0

        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        Instant completedJune3 = LocalDate.of(2026, 6, 3).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant completedJune6 = LocalDate.of(2026, 6, 6).atStartOfDay(ZoneOffset.UTC).toInstant();

        WorkItem item1 = workItem("wi-1", WorkItem.ResolvedStatus.DONE, 10.0, null, completedJune3);
        WorkItem item2 = workItem("wi-2", WorkItem.ResolvedStatus.DONE, 5.0, null, completedJune6);

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of(item1, item2));

        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        // Simulate "today" is June 8 by controlling seriesEnd — but since we can't mock LocalDate.now(),
        // we verify up to the actual current date. The important verification is that:
        // - On June 1: remainingSp = 15.0 (nothing completed yet)
        // - On June 3: remainingSp = 5.0 (item1 completed)
        // - On June 6: remainingSp = 0.0 (item2 completed)
        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        List<BurndownPoint> series = output.burndownSeries();
        assertThat(series).isNotEmpty();

        // June 1 entry (first entry, nothing completed)
        BurndownPoint june1 = series.stream()
                .filter(p -> p.date().equals(LocalDate.of(2026, 6, 1)))
                .findFirst().orElseThrow();
        assertThat(june1.remainingSp()).isEqualTo(15.0);
        // idealSp on day 0: totalSp * (1 - 0/9) = 15.0
        assertThat(june1.idealSp()).isEqualTo(15.0);

        // June 3 entry (item1 completed)
        BurndownPoint june3 = series.stream()
                .filter(p -> p.date().equals(LocalDate.of(2026, 6, 3)))
                .findFirst().orElseThrow();
        assertThat(june3.remainingSp()).isEqualTo(5.0);

        // June 6 entry (item2 completed)
        BurndownPoint june6 = series.stream()
                .filter(p -> p.date().equals(LocalDate.of(2026, 6, 6)))
                .findFirst().orElseThrow();
        assertThat(june6.remainingSp()).isEqualTo(0.0);

        // Both stories count toward the sprint SP denominator (delivered == total here)
        assertThat(output.totalSpInSprint()).isEqualTo(15.0);
        assertThat(output.storyPointsDelivered()).isEqualTo(15.0);
    }

    // ── totalSpInSprint: all stories vs. delivered ─────────────────────────────

    @Test
    void should_compute_totalSpInSprint_from_all_stories_not_only_done() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        WorkItem done = workItem("wi-1", WorkItem.ResolvedStatus.DONE, 5.0, 2.0, Instant.now());
        WorkItem inProgress = workItem("wi-2", WorkItem.ResolvedStatus.IN_PROGRESS, 8.0, null, null);

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of(done, inProgress));
        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        // delivered = DONE only (5), total = all stories (5 + 8 = 13)
        assertThat(output.storyPointsDelivered()).isEqualTo(5.0);
        assertThat(output.totalSpInSprint()).isEqualTo(13.0);
    }

    // ── Sprint token series: zero-filled across the full span ──────────────────

    @Test
    void should_zero_fill_tokensByDay_across_full_sprint_span() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of());
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        // Only two days have usage; the rest of the 10-day span must be zero-filled.
        DailyAiUsage june2 = new DailyAiUsage(LocalDate.of(2026, 6, 2), 1000L, 500L, 5L, new BigDecimal("0.05"));
        DailyAiUsage june5 = new DailyAiUsage(LocalDate.of(2026, 6, 5), 2000L, 800L, 9L, new BigDecimal("0.12"));
        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(
                eq(ORG), eq(USER), eq(SPRINT_START), eq(SPRINT_END), isNull()))
                .thenReturn(List.of(june2, june5));

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        List<DailyAiUsage> series = output.tokensByDay();
        // June 1..June 10 inclusive → 10 entries, one per day, in order.
        assertThat(series).hasSize(10);
        assertThat(series.get(0).day()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(series.get(9).day()).isEqualTo(LocalDate.of(2026, 6, 10));

        DailyAiUsage day1 = series.get(0);
        assertThat(day1.inputTokens()).isZero();
        assertThat(day1.outputTokens()).isZero();

        DailyAiUsage day2 = series.stream()
                .filter(d -> d.day().equals(LocalDate.of(2026, 6, 2))).findFirst().orElseThrow();
        assertThat(day2.inputTokens()).isEqualTo(1000L);
        assertThat(day2.outputTokens()).isEqualTo(500L);
    }

    // ── AI cost per work item: basis = the allocation's own usage records ──────

    @Test
    void should_attribute_aiCost_from_allocation_records_not_whole_day() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        WorkItem story = workItem("story-1", WorkItem.ResolvedStatus.IN_PROGRESS, 3.0, null, null);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of(story));

        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        // Two usage records on the same day: u1 ($0.10) is covered by the allocation,
        // u2 ($0.90) is NOT. Whole-day cost would be $1.00; correct basis is only $0.10.
        AIUsage u1 = aiUsage("u1", LocalDate.of(2026, 6, 2), "alloc-1", 1000L, 500L, "0.10");
        AIUsage u2 = aiUsage("u2", LocalDate.of(2026, 6, 2), null, 9000L, 4000L, "0.90");
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(
                eq(ORG), eq(USER), eq(SPRINT_START), eq(SPRINT_END)))
                .thenReturn(List.of(u1, u2));

        // Allocation covers only u1, fully attributed (100%) to the story.
        AiUsageAllocation alloc = new AiUsageAllocation(
                "alloc-1", ORG, USER, LocalDate.of(2026, 6, 2),
                List.of("u1"),
                List.of(new AllocationSplit("story-1", "Title story-1", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, 1L, Instant.now(), Instant.now());
        when(allocationRepository.findValidatedByOrgUserAndWorkItemId(ORG, USER, "story-1"))
                .thenReturn(List.of(alloc));

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        // Must be $0.10 (only u1), NOT $1.00 (whole day).
        assertThat(output.aiCostByWorkItemId()).containsKey("story-1");
        assertThat(output.aiCostByWorkItemId().get("story-1")).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    // ── Task 3.6b: aiSprintSummary unallocated days ────────────────────────────

    @Test
    void should_compute_aiSprintSummary_with_correct_unallocatedDaysCount() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of());
        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        // 3 usages: June 1 (unallocated), June 2 (unallocated), June 3 (allocated)
        AIUsage u1 = aiUsage("u1", LocalDate.of(2026, 6, 1), null, 1000L, 500L, "0.05");
        AIUsage u2 = aiUsage("u2", LocalDate.of(2026, 6, 2), null, 2000L, 1000L, "0.10");
        AIUsage u3 = aiUsage("u3", LocalDate.of(2026, 6, 3), "alloc-1", 500L, 250L, "0.02");

        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(
                eq(ORG), eq(USER), eq(SPRINT_START), eq(SPRINT_END)))
                .thenReturn(List.of(u1, u2, u3));

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        AiSprintSummary summary = output.aiSprintSummary();
        assertThat(summary).isNotNull();
        assertThat(summary.unallocatedDaysCount()).isEqualTo(2);
        assertThat(summary.totalTokens()).isEqualTo(1000 + 500 + 2000 + 1000 + 500 + 250);
        assertThat(summary.totalCostUsd()).isEqualByComparingTo(new BigDecimal("0.17"));
    }

    @Test
    void should_return_null_aiSprintSummary_when_no_usage_exists_in_sprint() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of());
        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        assertThat(output.aiSprintSummary()).isNull();
    }

    // ── Task 3.1/3.2: inProgressItems and totalSprintItems ────────────────────

    @Test
    void should_return_inProgressItems_and_totalSprintItems() {
        Sprint sprint = sprint(SPRINT_START, SPRINT_END);
        when(sprintRepository.findById(SPRINT_ID)).thenReturn(Optional.of(sprint));

        WorkItem inProgress = workItem("wi-1", WorkItem.ResolvedStatus.IN_PROGRESS, 3.0, null, null);
        WorkItem done = workItem("wi-2", WorkItem.ResolvedStatus.DONE, 5.0, 2.0, Instant.now());

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, SPRINT_ID))
                .thenReturn(List.of(inProgress, done));

        when(aiUsageRepository.findDailyUsageByUserIdAndDateRange(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(any(), any(), any(), any()))
                .thenReturn(List.of());

        DeveloperDashboardOutput output = useCase.execute(ORG, USER, null, SPRINT_ID, null, null, null);

        assertThat(output.totalSprintItems()).isEqualTo(2);
        assertThat(output.inProgressItems()).hasSize(1);
        assertThat(output.inProgressItems().get(0).getId()).isEqualTo("wi-1");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Sprint sprint(LocalDate start, LocalDate end) {
        Instant startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = end.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Sprint(SPRINT_ID, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                startInstant, endInstant,
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
    }

    private WorkItem workItem(String id, WorkItem.ResolvedStatus status,
                               Double storyPoints, Double cycleTimeHours, Instant completedAt) {
        return new WorkItem(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), "Title " + id,
                "Story", WorkItem.ResolvedType.STORY,
                "Active", status,
                "Medium", storyPoints, List.of(),
                SPRINT_ID, "ext-user", USER, null, null,
                Instant.now(), null, completedAt,
                cycleTimeHours, null, null, true, 1L, Instant.now());
    }

    private AIUsage aiUsage(String id, LocalDate day, String allocationId,
                             long inputTokens, long outputTokens, String costUsd) {
        return new AIUsage(
                id, ORG, USER, "gh-login", "dataset-1", day, "gpt-4o",
                "ws-1", "Workspace 1", "mc-1", "Machine 1",
                inputTokens, outputTokens, 5, "vscode", allocationId,
                new BigDecimal(costUsd), Instant.now(), true, null, Instant.now(), Instant.now()
        );
    }
}

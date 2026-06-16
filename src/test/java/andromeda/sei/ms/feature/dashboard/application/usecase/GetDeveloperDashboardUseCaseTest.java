package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.dashboard.application.dto.AiSprintSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDeveloperDashboardUseCaseTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;

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
        useCase = new GetDeveloperDashboardUseCase(sprintRepository, workItemRepository, aiUsageRepository);
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

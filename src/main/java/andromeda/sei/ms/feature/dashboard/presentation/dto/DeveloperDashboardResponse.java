package andromeda.sei.ms.feature.dashboard.presentation.dto;

import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DeveloperDashboardResponse(
        List<WorkItemSummary> currentSprintItems,
        @Schema(deprecated = true) Double avgCycleTimeHours,
        @Schema(deprecated = true) Double avgLeadTimeHours,
        Double storyPointsDelivered,
        Double totalSpInSprint,
        List<DailyUsageResponse> tokensByDay,
        BigDecimal sprintCostUsd,
        Double avgTokensPerSp,
        Integer openPrs,
        Integer mergedPrs,
        List<WorkItemSummaryDto> inProgressItems,
        int totalSprintItems,
        List<BurndownPointResponse> burndownSeries,
        AiSprintSummaryResponse aiSprintSummary,
        Map<String, BigDecimal> aiCostByWorkItemId,
        BigDecimal subscriptionCostPerUserUsd,
        List<SprintHistoryResponse> sprintHistory,
        andromeda.sei.ms.feature.dashboard.application.dto.IndividualSprintContext sprintContext,
        /** Frescura dos dados do projeto; {@code null} sem {@code projectId}. */
        DataFreshness dataFreshness,
        DurationStat cycleTime,
        DurationStat leadTime
) {

    public DeveloperDashboardResponse withDataFreshness(DataFreshness freshness) {
        return new DeveloperDashboardResponse(currentSprintItems, avgCycleTimeHours, avgLeadTimeHours, storyPointsDelivered, totalSpInSprint,
                tokensByDay, sprintCostUsd, avgTokensPerSp, openPrs, mergedPrs, inProgressItems, totalSprintItems,
                burndownSeries, aiSprintSummary, aiCostByWorkItemId, subscriptionCostPerUserUsd, sprintHistory,
                sprintContext, freshness, cycleTime, leadTime);
    }
    /**
     * complexity/effort and their sources are exposed so the dashboard can render the same
     * manual-override editor the backlog uses (WorkItemManualFieldsEditor) -- without the
     * *Source fields the UI cannot tell an Azure-synced value from a manually typed one.
     * storyPoints is kept for backwards compatibility: it carries the same value as effort.
     * type and externalId let the sprint-items cards identify the work item (badge + Azure id)
     * without reading the title.
     */
    public record WorkItemSummary(
            String id,
            String title,
            WorkItem.ResolvedStatus resolvedStatus,
            Double storyPoints,
            Double cycleTimeHours,
            Double leadTimeHours,
            Double tokensPerSp,
            Double complexity,
            String complexitySource,
            Double effort,
            String effortSource,
            WorkItem.ResolvedType type,
            String externalId
    ) {}

    /** One sprint of the comparison history (see SprintHistoryPoint in the application dto). */
    public record SprintHistoryResponse(
            String sprintId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            Double spPlanned,
            Double spDelivered,
            @Schema(deprecated = true) Double avgCycleTimeHours,
            Long totalTokens,
            BigDecimal totalCostUsd,
            Double cycleTimeP50
    ) {}

    public record WorkItemSummaryDto(
            String id,
            String title,
            WorkItem.ResolvedType type,
            Double storyPoints,
            String externalId
    ) {}

    public record DailyUsageResponse(
            LocalDate date,
            long inputTokens,
            long outputTokens,
            long interactions,
            BigDecimal costUsd
    ) {}

    public record BurndownPointResponse(LocalDate date, double remainingSp, double idealSp) {}

    public record AiSprintSummaryResponse(long totalTokens, BigDecimal totalCostUsd, int unallocatedDaysCount) {}
}

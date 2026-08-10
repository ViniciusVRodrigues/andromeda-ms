package andromeda.sei.ms.feature.dashboard.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DeveloperDashboardResponse(
        List<WorkItemSummary> currentSprintItems,
        Double avgCycleTimeHours,
        Double avgLeadTimeHours,
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
        BigDecimal subscriptionCostPerUserUsd
) {
    /**
     * complexity/effort and their sources are exposed so the dashboard can render the same
     * manual-override editor the backlog uses (WorkItemManualFieldsEditor) -- without the
     * *Source fields the UI cannot tell an Azure-synced value from a manually typed one.
     * storyPoints is kept for backwards compatibility: it carries the same value as effort.
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
            String effortSource
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

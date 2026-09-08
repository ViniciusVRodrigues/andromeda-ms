package andromeda.sei.ms.feature.dashboard.application.dto;

import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DeveloperDashboardOutput(
        List<WorkItem> currentSprintItems,
        Double avgCycleTimeHours,
        Double avgLeadTimeHours,
        Double storyPointsDelivered,
        Double totalSpInSprint,
        List<DailyAiUsage> tokensByDay,
        BigDecimal sprintCostUsd,
        Double avgTokensPerSp,
        Integer openPrs,
        Integer mergedPrs,
        List<WorkItem> inProgressItems,
        int totalSprintItems,
        List<BurndownPoint> burndownSeries,
        AiSprintSummary aiSprintSummary,
        Map<String, BigDecimal> aiCostByWorkItemId,
        BigDecimal subscriptionCostPerUserUsd,
        List<SprintHistoryPoint> sprintHistory,
        IndividualSprintContext sprintContext
) {
    public DeveloperDashboardOutput(List<WorkItem> currentSprintItems, Double avgCycleTimeHours,
            Double avgLeadTimeHours, Double storyPointsDelivered, Double totalSpInSprint,
            List<DailyAiUsage> tokensByDay, BigDecimal sprintCostUsd, Double avgTokensPerSp,
            Integer openPrs, Integer mergedPrs, List<WorkItem> inProgressItems, int totalSprintItems,
            List<BurndownPoint> burndownSeries, AiSprintSummary aiSprintSummary,
            Map<String, BigDecimal> aiCostByWorkItemId, BigDecimal subscriptionCostPerUserUsd,
            List<SprintHistoryPoint> sprintHistory) {
        this(currentSprintItems, avgCycleTimeHours, avgLeadTimeHours, storyPointsDelivered, totalSpInSprint,
                tokensByDay, sprintCostUsd, avgTokensPerSp, openPrs, mergedPrs, inProgressItems,
                totalSprintItems, burndownSeries, aiSprintSummary, aiCostByWorkItemId,
                subscriptionCostPerUserUsd, sprintHistory, null);
    }
}

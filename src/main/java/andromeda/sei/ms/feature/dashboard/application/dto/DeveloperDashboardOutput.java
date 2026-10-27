package andromeda.sei.ms.feature.dashboard.application.dto;

import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.dashboard.application.v2.ComplexityEffortFigures;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DeveloperDashboardOutput(
        List<WorkItem> currentSprintItems,
        Double avgCycleTimeHours,
        Double avgLeadTimeHours,
        List<DailyAiUsage> tokensByDay,
        BigDecimal sprintCostUsd,
        Integer openPrs,
        Integer mergedPrs,
        List<WorkItem> inProgressItems,
        int totalSprintItems,
        List<BurndownPoint> burndownSeries,
        AiSprintSummary aiSprintSummary,
        Map<String, BigDecimal> aiCostByWorkItemId,
        BigDecimal subscriptionCostPerUserUsd,
        List<SprintHistoryPoint> sprintHistory,
        IndividualSprintContext sprintContext,
        DurationStat cycleTime,
        DurationStat leadTime,
        ComplexityEffortFigures figures,
        EffortBearingLevelResolver.EffortScope effortScope
) {
    /** The v1 shape plus context and percentiles; the complexity/effort figures stay empty. */
    public DeveloperDashboardOutput(List<WorkItem> currentSprintItems, Double avgCycleTimeHours,
            Double avgLeadTimeHours,
            List<DailyAiUsage> tokensByDay, BigDecimal sprintCostUsd,
            Integer openPrs, Integer mergedPrs, List<WorkItem> inProgressItems, int totalSprintItems,
            List<BurndownPoint> burndownSeries, AiSprintSummary aiSprintSummary,
            Map<String, BigDecimal> aiCostByWorkItemId, BigDecimal subscriptionCostPerUserUsd,
            List<SprintHistoryPoint> sprintHistory, IndividualSprintContext sprintContext,
            DurationStat cycleTime, DurationStat leadTime) {
        this(currentSprintItems, avgCycleTimeHours, avgLeadTimeHours,
                tokensByDay, sprintCostUsd, openPrs, mergedPrs, inProgressItems,
                totalSprintItems, burndownSeries, aiSprintSummary, aiCostByWorkItemId,
                subscriptionCostPerUserUsd, sprintHistory, sprintContext, cycleTime, leadTime,
                ComplexityEffortFigures.EMPTY, null);
    }

    public DeveloperDashboardOutput(List<WorkItem> currentSprintItems, Double avgCycleTimeHours,
            Double avgLeadTimeHours,
            List<DailyAiUsage> tokensByDay, BigDecimal sprintCostUsd,
            Integer openPrs, Integer mergedPrs, List<WorkItem> inProgressItems, int totalSprintItems,
            List<BurndownPoint> burndownSeries, AiSprintSummary aiSprintSummary,
            Map<String, BigDecimal> aiCostByWorkItemId, BigDecimal subscriptionCostPerUserUsd,
            List<SprintHistoryPoint> sprintHistory) {
        this(currentSprintItems, avgCycleTimeHours, avgLeadTimeHours,
                tokensByDay, sprintCostUsd, openPrs, mergedPrs, inProgressItems,
                totalSprintItems, burndownSeries, aiSprintSummary, aiCostByWorkItemId,
                subscriptionCostPerUserUsd, sprintHistory, null, DurationStat.empty(), DurationStat.empty());
    }
}

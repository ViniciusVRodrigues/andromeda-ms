package andromeda.sei.ms.feature.dashboard.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamDashboardResponse(
        String sprintId,
        String sprintName,
        String sprintStartDate,
        String sprintEndDate,
        double teamVelocity,
        Double avgCycleTimeHours,
        Double avgLeadTimeHours,
        BigDecimal teamAiCostUsd,
        List<VelocityPointResponse> velocityTrend,
        List<MemberMetricsResponse> members
) {
    public record VelocityPointResponse(
            String sprintId,
            String sprintName,
            double storyPoints,
            BigDecimal aiCostUsd
    ) {}

    public record MemberMetricsResponse(
            String userId,
            String name,
            String avatarInitials,
            double storyPointsDelivered,
            Double cycleTimeHours,
            Double leadTimeHours,
            BigDecimal aiCostUsd,
            Double avgTokensPerSp,
            int workItemCount
    ) {}
}

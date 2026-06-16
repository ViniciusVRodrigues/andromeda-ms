package andromeda.sei.ms.feature.dashboard.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamDashboardOutput(
        String sprintId,
        String sprintName,
        String sprintStartDate,
        String sprintEndDate,
        double teamVelocity,
        Double avgCycleTimeHours,
        Double avgLeadTimeHours,
        BigDecimal teamAiCostUsd,
        List<VelocityPoint> velocityTrend,
        List<MemberMetrics> members
) {
    public record VelocityPoint(
            String sprintId,
            String sprintName,
            double storyPoints,
            BigDecimal aiCostUsd
    ) {}

    public record MemberMetrics(
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

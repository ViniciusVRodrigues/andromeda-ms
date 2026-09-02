package andromeda.sei.ms.feature.dashboard.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
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
        List<MemberMetricsResponse> members,
        List<StalePrSummaryResponse> stalePrs
) {

    public record StalePrSummaryResponse(
            String prId,
            String title,
            String authorName,
            Instant createdAt,
            double hoursOpen
    ) {}
    public record VelocityPointResponse(
            String sprintId,
            String sprintName,
            double spPlanned,
            double storyPoints,
            BigDecimal aiCostUsd,
            Long totalTokens
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

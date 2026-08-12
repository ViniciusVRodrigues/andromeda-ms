package andromeda.sei.ms.feature.dashboard.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
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
        List<MemberMetrics> members,
        List<StalePrSummary> stalePrs
) {

    /**
     * A PR that is open, has no first review yet, and has been open more than 24 hours
     * (design.md D3). authorName falls back to the raw GitHub login when the author has no
     * linked User account, so the list stays useful even for unlinked authors.
     */
    public record StalePrSummary(
            String prId,
            String title,
            String authorName,
            Instant createdAt,
            double hoursOpen
    ) {}
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

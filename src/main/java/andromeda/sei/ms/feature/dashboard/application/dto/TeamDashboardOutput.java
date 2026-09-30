package andromeda.sei.ms.feature.dashboard.application.dto;

import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Team dashboard of a project (team-dashboard). Velocity follows the single definition
 * (metric-aggregation-rules); durations come as p50/p90 ({@code cycleTime}/{@code leadTime}) with
 * the {@code avg*} fields kept, deprecated, for one release. {@code teamAiCostUsd} and each
 * member's {@code aiCostUsd} are the cost allocated to this project; cost not allocated yet is in
 * {@code unallocatedAiCostUsd}.
 */
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
        List<StalePrSummary> stalePrs,
        DurationStat cycleTime,
        DurationStat leadTime,
        ReviewMetrics review,
        BigDecimal unallocatedAiCostUsd
) {

    /**
     * A PR that is open, not a draft, has no first review yet, and has been open more than 24
     * hours (design.md D3). authorName falls back to the raw GitHub login when the author has no
     * linked User account, so the list stays useful even for unlinked authors.
     */
    public record StalePrSummary(
            String prId,
            String title,
            String authorName,
            Instant createdAt,
            double hoursOpen,
            /** Raw provider login of the author (vcs-core). */
            String authorLogin,
            /** VCS provider of the pull request ({@code GITHUB}, {@code AZURE_REPOS}). */
            String provider
    ) {}
    public record VelocityPoint(
            String sprintId,
            String sprintName,
            double spPlanned,
            double storyPoints,
            BigDecimal aiCostUsd,
            Long totalTokens
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
            int workItemCount,
            DurationStat cycleTime,
            DurationStat leadTime,
            BigDecimal unallocatedAiCostUsd
    ) {}
}

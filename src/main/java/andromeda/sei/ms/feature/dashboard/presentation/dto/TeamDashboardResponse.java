package andromeda.sei.ms.feature.dashboard.presentation.dto;

import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code teamAiCostUsd} and members' {@code aiCostUsd}: cost allocated to this project
 * ({@code VALIDATED} allocations to its work items); {@code unallocatedAiCostUsd}: members' cost
 * not allocated yet. Durations in hours; {@code avg*} deprecated in favour of p50/p90.
 */
public record TeamDashboardResponse(
        String sprintId,
        String sprintName,
        String sprintStartDate,
        String sprintEndDate,
        double teamVelocity,
        @Schema(deprecated = true) Double avgCycleTimeHours,
        @Schema(deprecated = true) Double avgLeadTimeHours,
        BigDecimal teamAiCostUsd,
        List<VelocityPointResponse> velocityTrend,
        List<MemberMetricsResponse> members,
        List<StalePrSummaryResponse> stalePrs,
        /** Frescura dos dados do projeto; {@code null} sem {@code projectId}. */
        DataFreshness dataFreshness,
        DurationStat cycleTime,
        DurationStat leadTime,
        ReviewMetrics review,
        BigDecimal unallocatedAiCostUsd
) {

    public TeamDashboardResponse withDataFreshness(DataFreshness freshness) {
        return new TeamDashboardResponse(sprintId, sprintName, sprintStartDate, sprintEndDate, teamVelocity, avgCycleTimeHours,
                avgLeadTimeHours, teamAiCostUsd, velocityTrend, members, stalePrs, freshness,
                cycleTime, leadTime, review, unallocatedAiCostUsd);
    }

    public record StalePrSummaryResponse(
            String prId,
            String title,
            String authorName,
            Instant createdAt,
            double hoursOpen,
            String authorLogin,
            String provider,
            /** Deprecated copy of {@code authorLogin}, kept for one release (vcs-core-refactor). */
            @io.swagger.v3.oas.annotations.media.Schema(deprecated = true) String authorGithubLogin
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
            @Schema(deprecated = true) Double cycleTimeHours,
            @Schema(deprecated = true) Double leadTimeHours,
            BigDecimal aiCostUsd,
            Double avgTokensPerSp,
            int workItemCount,
            DurationStat cycleTime,
            DurationStat leadTime,
            BigDecimal unallocatedAiCostUsd
    ) {}
}

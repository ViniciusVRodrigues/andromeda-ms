package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.shared.domain.metrics.DurationStat;

/**
 * Aggregated review metrics of a set of pull requests (review-metrics).
 *
 * <p>{@code timeToFirstReview} and {@code timeToFirstApproval} cover reviewed (respectively
 * approved) pull requests only — an unreviewed pull request has no duration to rank. Pull requests
 * still waiting are what {@code reviewedWithinSlaPercent} captures: they count against it once
 * they are older than {@code slaHours}.
 */
public record ReviewMetrics(
        DurationStat timeToFirstReview,
        DurationStat timeToFirstApproval,
        Double reviewedWithinSlaPercent,
        int eligiblePrs,
        int slaHours) {

    public static ReviewMetrics empty() {
        return new ReviewMetrics(DurationStat.empty(), DurationStat.empty(), null, 0,
                (int) ReviewMetricsPolicy.REVIEW_SLA_HOURS);
    }
}

package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.shared.domain.metrics.Ratios;

import java.time.Instant;

/**
 * When an open pull request counts as stalled: nobody has reviewed it and it has been waiting
 * longer than the threshold.
 *
 * <p>Only a pull request with status OPEN can be stale: a merged or closed one is no longer waiting
 * for anybody.
 *
 * <p>Lives here so the team dashboard and the repository health summary cannot drift apart. The
 * threshold is the review SLA (review-metrics), and drafts are never stale: a draft is not waiting
 * for review yet.
 */
public final class StalePullRequestPolicy {

    public static final long STALE_PR_THRESHOLD_HOURS = ReviewMetricsPolicy.REVIEW_SLA_HOURS;

    private StalePullRequestPolicy() {}

    public static boolean isStale(VcsPullRequest pr, Instant now) {
        if (pr == null || pr.getCreatedAt() == null) return false;
        if (pr.getStatus() != PullRequestStatus.OPEN) return false;
        if (pr.isDraft()) return false;
        if (pr.getTimeToFirstReviewHours() != null) return false;
        // Fractional hours, like the SLA eligibility of ReviewMetricsPolicy: both must agree at 24.5h.
        return Ratios.hours(pr.getCreatedAt(), now) > STALE_PR_THRESHOLD_HOURS;
    }
}

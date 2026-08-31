package andromeda.sei.ms.feature.github.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * When an open pull request counts as stalled: nobody has reviewed it and it has been waiting
 * longer than the threshold.
 *
 * <p>Lives here so the team dashboard and the repository health summary cannot drift apart.
 * Two surfaces reporting different counts under the same name would be a trust bug: the user
 * has no way to tell which one is lying.
 */
public final class StalePullRequestPolicy {

    public static final long STALE_PR_THRESHOLD_HOURS = 24;

    private StalePullRequestPolicy() {}

    public static boolean isStale(GithubPullRequest pr, Instant now) {
        if (pr == null || pr.getCreatedAt() == null) return false;
        if (pr.getTimeToFirstReviewHours() != null) return false;
        return Duration.between(pr.getCreatedAt(), now).toHours() > STALE_PR_THRESHOLD_HOURS;
    }
}

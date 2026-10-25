package andromeda.sei.ms.feature.vcs.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StalePullRequestPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void should_notBeStale_when_mergedWithoutReviewForMoreThan24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.MERGED, NOW.minusSeconds(48 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_closedWithoutReviewForMoreThan24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.CLOSED, NOW.minusSeconds(48 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_statusIsNull() {
        assertThat(StalePullRequestPolicy.isStale(pr(null, NOW.minusSeconds(48 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_beStale_when_openWithoutReviewForMoreThan24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600)), NOW)).isTrue();
    }

    @Test
    void should_notBeStale_when_openAndAlreadyReviewed() {
        VcsPullRequest pr = pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600));
        pr.setTimeToFirstReviewHours(3.0);
        assertThat(StalePullRequestPolicy.isStale(pr, NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_openDraft() {
        VcsPullRequest pr = pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600));
        pr.setDraft(true);
        assertThat(StalePullRequestPolicy.isStale(pr, NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_openForExactly24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.OPEN, NOW.minusSeconds(24 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_beStale_when_openFor24AndAHalfHours() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.OPEN, NOW.minusSeconds(24 * 3600 + 1800)), NOW)).isTrue();
    }

    private static VcsPullRequest pr(PullRequestStatus status, Instant createdAt) {
        VcsPullRequest pr = new VcsPullRequest("pr-1", "org", "repo", VcsProvider.GITHUB, "ext-1", 1, "PR", status);
        pr.setCreatedAt(createdAt);
        return pr;
    }
}

package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures.LegacyReviewResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewMetricsPolicyTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    // ---- qualifying signal / per-PR durations

    @Test
    void should_haveNoQualifyingSignal_when_onlyTheAuthorReviewed() {
        VcsPullRequest pr = pr(CREATED);

        Double ttfr = ReviewMetricsPolicy.timeToFirstReviewHours(pr,
                List.of(review("octocat", hoursAfter(1), LegacyReviewResult.COMMENTED)));

        assertThat(ttfr).isNull();
    }

    @Test
    void should_matchAuthorByUserId_when_loginsDiffer() {
        VcsPullRequest pr = pr(CREATED);
        pr.setAuthorUserId("user-1");
        VcsReviewEvent review = review("renamed-login", hoursAfter(1), LegacyReviewResult.COMMENTED);
        review.setActorUserId("user-1");

        assertThat(ReviewMetricsPolicy.isQualifying(pr, review)).isFalse();
    }

    @Test
    void should_ignoreBotAndUseFirstHuman_when_botCommentsFirst() {
        VcsPullRequest pr = pr(CREATED);
        VcsReviewEvent bot = review("dependabot[bot]", hoursAfter(1), LegacyReviewResult.COMMENTED);
        VcsReviewEvent flaggedBot = review("ci-helper", hoursAfter(2), LegacyReviewResult.COMMENTED);
        flaggedBot.setBot(true);
        VcsReviewEvent human = review("alice", Instant.parse("2026-09-01T13:30:00Z"), LegacyReviewResult.COMMENTED);

        Double ttfr = ReviewMetricsPolicy.timeToFirstReviewHours(pr, List.of(bot, flaggedBot, human));

        assertThat(ttfr).isEqualTo(3.5);
    }

    @Test
    void should_ignorePendingReviews_when_noSubmissionTime() {
        VcsPullRequest pr = pr(CREATED);

        Double ttfr = ReviewMetricsPolicy.timeToFirstReviewHours(pr,
                List.of(review("alice", null, null)));

        assertThat(ttfr).isNull();
    }

    @Test
    void should_countDismissedAsReviewButNotApproval_when_dismissed() {
        VcsPullRequest pr = pr(CREATED);
        List<VcsReviewEvent> reviews = List.of(review("alice", hoursAfter(4), LegacyReviewResult.DISMISSED));

        assertThat(ReviewMetricsPolicy.timeToFirstReviewHours(pr, reviews)).isEqualTo(4.0);
        assertThat(ReviewMetricsPolicy.timeToFirstApprovalHours(pr, reviews)).isNull();
    }

    @Test
    void should_separateFirstReviewAndFirstApproval_when_commentThenApproval() {
        VcsPullRequest pr = pr(CREATED);
        List<VcsReviewEvent> reviews = List.of(
                review("bob", hoursAfter(20), LegacyReviewResult.APPROVED),
                review("alice", hoursAfter(2), LegacyReviewResult.COMMENTED));

        assertThat(ReviewMetricsPolicy.timeToFirstReviewHours(pr, reviews)).isEqualTo(2.0);
        assertThat(ReviewMetricsPolicy.timeToFirstApprovalHours(pr, reviews)).isEqualTo(20.0);
    }

    @Test
    void should_ignoreInactiveReviews_when_computing() {
        VcsPullRequest pr = pr(CREATED);
        VcsReviewEvent deleted = review("alice", hoursAfter(1), LegacyReviewResult.APPROVED);
        deleted.setActive(false);

        assertThat(ReviewMetricsPolicy.timeToFirstReviewHours(pr, List.of(deleted))).isNull();
    }

    // ---- aggregation / SLA

    @Test
    void should_report70PercentWithinSla_when_7of10EligibleReviewedInTime() {
        List<VcsPullRequest> prs = new ArrayList<>();
        for (int i = 0; i < 7; i++) prs.add(reviewed(CREATED, 2.0 + i));
        prs.add(reviewed(CREATED, 30.0));
        prs.add(unreviewedOpen(NOW.minus(Duration.ofHours(48))));
        prs.add(unreviewedOpen(NOW.minus(Duration.ofHours(48))));

        ReviewMetrics metrics = ReviewMetricsPolicy.aggregate(prs, NOW);

        assertThat(metrics.reviewedWithinSlaPercent()).isEqualTo(70.0);
        assertThat(metrics.eligiblePrs()).isEqualTo(10);
        assertThat(metrics.timeToFirstReview().n()).isEqualTo(8);
        assertThat(metrics.timeToFirstReview().p50()).isEqualTo(5.0);
        assertThat(metrics.timeToFirstReview().p90()).isEqualTo(30.0);
        assertThat(metrics.slaHours()).isEqualTo(24);
    }

    @Test
    void should_notJudgeYoungPullRequest_when_unreviewedAndUnderSla() {
        ReviewMetrics metrics = ReviewMetricsPolicy.aggregate(
                List.of(unreviewedOpen(NOW.minus(Duration.ofHours(3)))), NOW);

        assertThat(metrics.eligiblePrs()).isZero();
        assertThat(metrics.reviewedWithinSlaPercent()).isNull();
    }

    @Test
    void should_useLifetimeUntilClosed_when_closedWithoutReview() {
        VcsPullRequest closedFast = pr(CREATED);
        closedFast.setClosedAt(CREATED.plus(Duration.ofHours(5)));

        ReviewMetrics metrics = ReviewMetricsPolicy.aggregate(List.of(closedFast), NOW);

        assertThat(metrics.eligiblePrs()).isZero();
    }

    @Test
    void should_excludeDrafts_when_aggregating() {
        VcsPullRequest oldDraft = unreviewedOpen(NOW.minus(Duration.ofDays(5)));
        oldDraft.setDraft(true);

        ReviewMetrics metrics = ReviewMetricsPolicy.aggregate(List.of(oldDraft), NOW);

        assertThat(metrics.eligiblePrs()).isZero();
        assertThat(metrics.timeToFirstReview().n()).isZero();
    }

    @Test
    void should_notListDraftAsStale_when_oldAndUnreviewed() {
        VcsPullRequest oldDraft = unreviewedOpen(NOW.minus(Duration.ofDays(5)));
        oldDraft.setDraft(true);

        assertThat(StalePullRequestPolicy.isStale(oldDraft, NOW)).isFalse();
        assertThat(StalePullRequestPolicy.isStale(unreviewedOpen(NOW.minus(Duration.ofDays(5))), NOW)).isTrue();
        assertThat(StalePullRequestPolicy.STALE_PR_THRESHOLD_HOURS).isEqualTo(ReviewMetricsPolicy.REVIEW_SLA_HOURS);
    }

    @Test
    void should_returnEmptyAggregate_when_noPullRequests() {
        ReviewMetrics metrics = ReviewMetricsPolicy.aggregate(List.of(), NOW);

        assertThat(metrics).isEqualTo(ReviewMetrics.empty());
    }

    // ---- neutral review kinds (vcs-core D3)

    @Test
    void should_countApprovalWithSuggestions_asApproval() {
        VcsPullRequest pr = pr(CREATED);
        VcsReviewEvent vote = event("carol", hoursAfter(5), ReviewKind.APPROVED_WITH_SUGGESTIONS);

        assertThat(ReviewMetricsPolicy.timeToFirstApprovalHours(pr, List.of(vote))).isEqualTo(5.0);
    }

    @Test
    void should_countRejection_asReviewButNotApproval() {
        VcsPullRequest pr = pr(CREATED);
        VcsReviewEvent rejection = event("carol", hoursAfter(2), ReviewKind.REJECTED);

        assertThat(ReviewMetricsPolicy.timeToFirstReviewHours(pr, List.of(rejection))).isEqualTo(2.0);
        assertThat(ReviewMetricsPolicy.timeToFirstApprovalHours(pr, List.of(rejection))).isNull();
    }

    @Test
    void should_ignoreDeletedEvents() {
        VcsPullRequest pr = pr(CREATED);
        VcsReviewEvent withdrawn = event("carol", hoursAfter(1), ReviewKind.APPROVED);
        withdrawn.setDeleted(true);

        assertThat(ReviewMetricsPolicy.isQualifying(pr, withdrawn)).isFalse();
    }

    @Test
    void should_matchAuthorByExternalId_when_loginsAndUsersDiffer() {
        VcsPullRequest pr = pr(CREATED);
        pr.setAuthorExternalId("583231");
        VcsReviewEvent self = event("renamed", hoursAfter(1), ReviewKind.COMMENTED);
        self.setActorExternalId("583231");

        assertThat(ReviewMetricsPolicy.isQualifying(pr, self)).isFalse();
    }

    @Test
    void should_treatMigratedDismissedReview_asCommentNotApproval() {
        VcsPullRequest pr = pr(CREATED);
        VcsReviewEvent dismissed = review("alice", hoursAfter(3), LegacyReviewResult.DISMISSED);

        assertThat(dismissed.getKind()).isEqualTo(ReviewKind.COMMENTED);
        assertThat(ReviewMetricsPolicy.timeToFirstReviewHours(pr, List.of(dismissed))).isEqualTo(3.0);
        assertThat(ReviewMetricsPolicy.timeToFirstApprovalHours(pr, List.of(dismissed))).isNull();
    }

    private VcsReviewEvent event(String login, Instant occurredAt, ReviewKind kind) {
        VcsReviewEvent event = new VcsReviewEvent(null, "org-1", "pr-1", "repo-1", VcsProvider.GITHUB,
                "evt-" + login, kind, occurredAt);
        event.setActorLogin(login);
        return event;
    }

    private Instant hoursAfter(long hours) {
        return CREATED.plus(Duration.ofHours(hours));
    }

    private VcsPullRequest reviewed(Instant createdAt, double ttfr) {
        VcsPullRequest pr = pr(createdAt);
        pr.setTimeToFirstReviewHours(ttfr);
        return pr;
    }

    private VcsPullRequest unreviewedOpen(Instant createdAt) {
        return pr(createdAt);
    }

    private VcsPullRequest pr(Instant createdAt) {
        return VcsLegacyFixtures.pullRequest("pr-" + createdAt.toEpochMilli(), "org-1", "repo-1", "ext", 1, "title",
                PullRequestStatus.OPEN, "octocat", null, createdAt, null, null, createdAt,
                null, null, true, createdAt, null);
    }

    private VcsReviewEvent review(String login, Instant submittedAt, LegacyReviewResult result) {
        return VcsLegacyFixtures.reviewEvent(null, "org-1", "pr-1", "rev-" + login, login, null, result, submittedAt,
                true, CREATED, CREATED, null);
    }
}

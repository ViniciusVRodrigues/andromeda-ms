package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.domain.metrics.Ratios;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The single definition of review metrics (review-metrics, metrics-correctness D4), operating on
 * normalized {@link VcsReviewEvent}s (vcs-core D3) so every provider shares it. Pure domain logic.
 *
 * <h2>Qualifying review signal</h2>
 * An event counts when it is active, not deleted, has an occurrence time and a kind, and comes
 * from someone other than the author who is not a bot. Any kind is a review; only
 * {@link ReviewKind#isApproval() approvals} count for time to first approval. A GitHub review that
 * was later dismissed is stored as {@code COMMENTED} (its original state is lost), so it still
 * counts as a review and never as an approval — the pre-refactor rule.
 *
 * <h2>SLA</h2>
 * The review SLA is the same threshold as the stale pull request list, so "reviewed within SLA"
 * and "stale" can never disagree about the same pull request.
 */
public final class ReviewMetricsPolicy {

    public static final long REVIEW_SLA_HOURS = 24;

    private static final String BOT_LOGIN_SUFFIX = "[bot]";

    private ReviewMetricsPolicy() {}

    public static boolean isQualifying(VcsPullRequest pr, VcsReviewEvent event) {
        if (event == null || !event.isActive() || event.isDeleted()) return false;
        if (event.getOccurredAt() == null || event.getKind() == null) return false;
        return !isBot(event) && !isAuthor(pr, event);
    }

    /** Hours from creation to the first qualifying review, 4 decimals; {@code null} when none yet. */
    public static Double timeToFirstReviewHours(VcsPullRequest pr, Collection<VcsReviewEvent> events) {
        return hoursFromCreation(pr, firstSignalAt(pr, events, false));
    }

    /** Hours from creation to the first qualifying approval, 4 decimals; {@code null} when none yet. */
    public static Double timeToFirstApprovalHours(VcsPullRequest pr, Collection<VcsReviewEvent> events) {
        return hoursFromCreation(pr, firstSignalAt(pr, events, true));
    }

    /**
     * Aggregates pull requests created in the window. Drafts are excluded. A pull request is
     * eligible for the SLA once it was reviewed or once it has waited longer than the SLA (until
     * now while open, until it was closed or merged otherwise); a young unreviewed pull request is
     * not judged yet.
     */
    public static ReviewMetrics aggregate(Collection<VcsPullRequest> pullRequests, Instant now) {
        List<VcsPullRequest> nonDraft = pullRequests.stream()
                .filter(Objects::nonNull)
                .filter(pr -> !pr.isDraft() && pr.getCreatedAt() != null)
                .toList();

        List<VcsPullRequest> eligible = nonDraft.stream().filter(pr -> isSlaEligible(pr, now)).toList();
        long withinSla = eligible.stream()
                .filter(pr -> pr.getTimeToFirstReviewHours() != null
                        && pr.getTimeToFirstReviewHours() <= REVIEW_SLA_HOURS)
                .count();

        return new ReviewMetrics(
                Percentiles.of(nonDraft.stream().map(VcsPullRequest::getTimeToFirstReviewHours).toList()),
                Percentiles.of(nonDraft.stream().map(VcsPullRequest::getTimeToFirstApprovalHours).toList()),
                Ratios.percent(withinSla, eligible.size()),
                eligible.size(),
                (int) REVIEW_SLA_HOURS);
    }

    static boolean isSlaEligible(VcsPullRequest pr, Instant now) {
        if (pr.getTimeToFirstReviewHours() != null) return true;
        Instant end = pr.getMergedAt() != null ? pr.getMergedAt()
                : pr.getClosedAt() != null ? pr.getClosedAt() : now;
        return Ratios.hours(pr.getCreatedAt(), end) > REVIEW_SLA_HOURS;
    }

    private static Optional<Instant> firstSignalAt(VcsPullRequest pr, Collection<VcsReviewEvent> events,
                                                   boolean approvalsOnly) {
        if (events == null) return Optional.empty();
        return events.stream()
                .filter(event -> isQualifying(pr, event))
                .filter(event -> !approvalsOnly || event.getKind().isApproval())
                .map(VcsReviewEvent::getOccurredAt)
                .min(Comparator.naturalOrder());
    }

    private static Double hoursFromCreation(VcsPullRequest pr, Optional<Instant> signalAt) {
        if (pr.getCreatedAt() == null || signalAt.isEmpty()) return null;
        return Ratios.hours(pr.getCreatedAt(), signalAt.get());
    }

    /** Persisted flag first; the {@code [bot]} login suffix still identifies legacy bots. */
    private static boolean isBot(VcsReviewEvent event) {
        if (event.isBot()) return true;
        String login = event.getActorLogin();
        return login != null && login.endsWith(BOT_LOGIN_SUFFIX);
    }

    /** Whether the event was made by the pull request's author (a self comment is not a review). */
    public static boolean isAuthor(VcsPullRequest pr, VcsReviewEvent event) {
        String authorLogin = pr.getAuthorLogin();
        if (authorLogin != null && authorLogin.equalsIgnoreCase(event.getActorLogin())) return true;
        if (pr.getAuthorExternalId() != null && pr.getAuthorExternalId().equals(event.getActorExternalId())) {
            return true;
        }
        return pr.getAuthorUserId() != null && pr.getAuthorUserId().equals(event.getActorUserId());
    }
}

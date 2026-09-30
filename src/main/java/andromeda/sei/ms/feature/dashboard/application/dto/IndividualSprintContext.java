package andromeda.sei.ms.feature.dashboard.application.dto;

import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;

import java.time.LocalDate;
import java.util.List;

/**
 * Pull request context of the developer's window. {@code avgTimeToFirstReviewHours} and
 * {@code avgPrCycleTimeHours} are deprecated (one release) in favour of {@code review}
 * (review-metrics: p50/p90 over reviewed PRs, SLA %) and {@code prCycleTime} (p50/p90 over merged
 * PRs). When a project is informed, only PRs of repositories bound to it count.
 */
public record IndividualSprintContext(String sprintId, String sprintName, LocalDate startDate, LocalDate endDate,
        Double avgTimeToFirstReviewHours, Double avgPrCycleTimeHours, int reviewedPrs,
        List<PullRequest> pullRequests, ReviewMetrics review, DurationStat prCycleTime) {
    /**
     * {@code authorLogin} and {@code provider} come from the neutral model (vcs-core);
     * {@code authorGithubLogin} is a deprecated copy of {@code authorLogin}, kept for one release.
     */
    public record PullRequest(String id, Integer number, String title, String status,
            Double timeToFirstReviewHours, Double cycleTimeHours, String linkedWorkItemExternalId,
            String authorLogin, String provider,
            @io.swagger.v3.oas.annotations.media.Schema(deprecated = true) String authorGithubLogin) {}
}

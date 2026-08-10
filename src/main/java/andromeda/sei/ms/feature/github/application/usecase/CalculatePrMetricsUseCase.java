package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubReview;
import andromeda.sei.ms.feature.github.domain.GithubReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Computes {@code cycleTimeHours} and {@code timeToFirstReviewHours} per pull request, matching
 * {@code CalculateWorkItemMetricsUseCase}'s rounding convention exactly (epoch-millis / 3.6M,
 * rounded to 4 decimal places) for consistency across the codebase (design.md D6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculatePrMetricsUseCase {

    private static final double MILLIS_PER_HOUR = 3_600_000.0;
    private static final double ROUNDING_SCALE = 10_000.0;

    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubReviewRepository githubReviewRepository;

    public void execute(String organizationId, String repoId) {
        List<GithubPullRequest> pullRequests =
                githubPullRequestRepository.findAllByOrganizationIdAndRepoId(organizationId, repoId);

        for (GithubPullRequest pr : pullRequests) {
            Double cycleTimeHours = computeCycleTime(pr);
            Double timeToFirstReviewHours = computeTimeToFirstReview(organizationId, pr);

            githubPullRequestRepository.updateMetrics(pr.getId(), cycleTimeHours, timeToFirstReviewHours);
        }
    }

    /** spec: PR cycle time -- (mergedAt ?? closedAt) - createdAt; null while still open. */
    private Double computeCycleTime(GithubPullRequest pr) {
        Instant endpoint = pr.getMergedAt() != null ? pr.getMergedAt() : pr.getClosedAt();
        if (endpoint == null || pr.getCreatedAt() == null) {
            return null;
        }
        return roundHours(endpoint.toEpochMilli() - pr.getCreatedAt().toEpochMilli());
    }

    /** spec: TTFR -- earliest review submitted_at (any state) - createdAt; null with zero reviews. */
    private Double computeTimeToFirstReview(String organizationId, GithubPullRequest pr) {
        if (pr.getCreatedAt() == null) {
            return null;
        }
        List<GithubReview> reviews =
                githubReviewRepository.findAllByOrganizationIdAndPullRequestId(organizationId, pr.getId());

        Instant earliest = reviews.stream()
                .map(GithubReview::getSubmittedAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (earliest == null) {
            return null;
        }
        return roundHours(earliest.toEpochMilli() - pr.getCreatedAt().toEpochMilli());
    }

    private Double roundHours(long deltaMillis) {
        double raw = deltaMillis / MILLIS_PER_HOUR;
        return Math.round(raw * ROUNDING_SCALE) / ROUNDING_SCALE;
    }
}

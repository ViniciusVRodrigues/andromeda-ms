package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes {@code cycleTimeHours}, {@code timeToFirstReviewHours} and
 * {@code timeToFirstApprovalHours} of every pull request of a repository, from its
 * {@code vcs_review_events} only (vcs-core D3), whatever the provider. Hours rounded to 4 decimals.
 * All events of the repository are read in one query instead of one per pull request.
 */
@Service
@RequiredArgsConstructor
public class CalculatePullRequestMetricsUseCase {

    private final VcsPullRequestRepository pullRequestRepository;
    private final VcsReviewEventRepository reviewEventRepository;

    public void execute(String organizationId, String repositoryId) {
        List<VcsPullRequest> pullRequests =
                pullRequestRepository.findAllByOrganizationIdAndRepositoryId(organizationId, repositoryId);
        if (pullRequests.isEmpty()) return;

        Map<String, List<VcsReviewEvent>> eventsByPullRequest = reviewEventRepository
                .findAllByOrganizationIdAndPullRequestIdIn(organizationId,
                        pullRequests.stream().map(VcsPullRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(VcsReviewEvent::getPullRequestId));

        for (VcsPullRequest pr : pullRequests) {
            List<VcsReviewEvent> events = eventsByPullRequest.getOrDefault(pr.getId(), List.of());
            pullRequestRepository.updateMetrics(organizationId, pr.getId(),
                    cycleTimeHours(pr),
                    ReviewMetricsPolicy.timeToFirstReviewHours(pr, events),
                    ReviewMetricsPolicy.timeToFirstApprovalHours(pr, events));
        }
    }

    /** PR cycle time: {@code (mergedAt ?? closedAt) - createdAt}; null while still open. */
    static Double cycleTimeHours(VcsPullRequest pr) {
        Instant endpoint = pr.getMergedAt() != null ? pr.getMergedAt() : pr.getClosedAt();
        if (endpoint == null || pr.getCreatedAt() == null) return null;
        return Ratios.hours(pr.getCreatedAt(), endpoint);
    }
}

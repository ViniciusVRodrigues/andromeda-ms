package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GithubPullRequestRepository {

    Optional<GithubPullRequest> findByOrganizationIdAndRepoIdAndExternalId(
            String organizationId, String repoId, String externalId);

    List<GithubPullRequest> findAllByOrganizationIdAndRepoId(String organizationId, String repoId);

    List<GithubPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to);

    List<GithubPullRequest> findAllByOrganizationIdAndRepoIdInAndStatus(
            String organizationId, List<String> repoIds, PullRequestStatus status);

    GithubPullRequest save(GithubPullRequest pullRequest);

    void updateMetrics(String id, Double cycleTimeHours, Double timeToFirstReviewHours);
}

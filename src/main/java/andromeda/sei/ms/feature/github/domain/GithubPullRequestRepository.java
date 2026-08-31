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

 /**
     * One page of a repository pull requests, newest first, optionally narrowed by status
     * and author. A null filter means "any".
     */
    GithubPullRequestPage findPageByRepo(String organizationId, String repoId,
                                          PullRequestStatus status, String authorUserId,
                                          int page, int size);

    /** Distinct author logins seen on a repository, for the author filter. */
    List<String> findDistinctAuthorLoginsByRepo(String organizationId, String repoId);

    Optional<GithubPullRequest> findByIdAndOrganizationId(String id, String organizationId);

    GithubPullRequest save(GithubPullRequest pullRequest);

    void updateMetrics(String id, Double cycleTimeHours, Double timeToFirstReviewHours);
}

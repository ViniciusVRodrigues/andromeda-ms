package andromeda.sei.ms.feature.github.domain;

import java.util.List;
import java.util.Optional;

public interface GithubPullRequestRepository {

    Optional<GithubPullRequest> findByOrganizationIdAndRepoIdAndExternalId(
            String organizationId, String repoId, String externalId);

    List<GithubPullRequest> findAllByOrganizationIdAndRepoId(String organizationId, String repoId);

    GithubPullRequest save(GithubPullRequest pullRequest);

    void updateMetrics(String id, Double cycleTimeHours, Double timeToFirstReviewHours);
}

package andromeda.sei.ms.feature.github.domain;

import java.util.List;
import java.util.Optional;

public interface GithubReviewRepository {

    Optional<GithubReview> findByOrganizationIdAndPullRequestIdAndExternalId(
            String organizationId, String pullRequestId, String externalId);

    List<GithubReview> findAllByOrganizationIdAndPullRequestId(String organizationId, String pullRequestId);

    GithubReview save(GithubReview review);
}

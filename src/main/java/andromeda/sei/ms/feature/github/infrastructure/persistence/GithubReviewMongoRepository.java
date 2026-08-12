package andromeda.sei.ms.feature.github.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GithubReviewMongoRepository extends MongoRepository<GithubReviewDocument, String> {

    Optional<GithubReviewDocument> findByOrganizationIdAndPullRequestIdAndExternalId(
            String organizationId, String pullRequestId, String externalId);

    List<GithubReviewDocument> findAllByOrganizationIdAndPullRequestId(String organizationId, String pullRequestId);
}

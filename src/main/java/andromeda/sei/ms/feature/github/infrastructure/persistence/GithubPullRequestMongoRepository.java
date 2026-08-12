package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GithubPullRequestMongoRepository extends MongoRepository<GithubPullRequestDocument, String> {

    Optional<GithubPullRequestDocument> findByOrganizationIdAndRepoIdAndExternalId(
            String organizationId, String repoId, String externalId);

    List<GithubPullRequestDocument> findAllByOrganizationIdAndRepoId(String organizationId, String repoId);

    List<GithubPullRequestDocument> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to);

    List<GithubPullRequestDocument> findAllByOrganizationIdAndRepoIdInAndStatus(
            String organizationId, List<String> repoIds, PullRequestStatus status);
}

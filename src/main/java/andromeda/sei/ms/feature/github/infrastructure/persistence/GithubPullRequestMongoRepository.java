package andromeda.sei.ms.feature.github.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GithubPullRequestMongoRepository extends MongoRepository<GithubPullRequestDocument, String> {

    Optional<GithubPullRequestDocument> findByOrganizationIdAndRepoIdAndExternalId(
            String organizationId, String repoId, String externalId);

    List<GithubPullRequestDocument> findAllByOrganizationIdAndRepoId(String organizationId, String repoId);
}

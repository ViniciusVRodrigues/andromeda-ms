package andromeda.sei.ms.feature.github.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GithubRepoMongoRepository extends MongoRepository<GithubRepoDocument, String> {

    Optional<GithubRepoDocument> findByOrganizationIdAndExternalId(String organizationId, String externalId);

    Optional<GithubRepoDocument> findByIdAndOrganizationId(String id, String organizationId);

    List<GithubRepoDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);
}

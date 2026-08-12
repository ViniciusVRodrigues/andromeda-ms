package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GithubConfigMongoRepository extends MongoRepository<GithubConfigDocument, String> {

    Optional<GithubConfigDocument> findByOrganizationIdAndActiveTrue(String organizationId);

    List<GithubConfigDocument> findAllByStatusAndActiveTrue(GithubConnectionStatus status);
}

package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorTokenMongoRepository extends MongoRepository<ConnectorTokenDocument, String> {

    Optional<ConnectorTokenDocument> findByIdAndOrganizationId(String id, String organizationId);

    Optional<ConnectorTokenDocument> findByOrganizationIdAndTokenHash(String organizationId, String tokenHash);

    List<ConnectorTokenDocument> findAllByOrganizationIdAndUserIdAndActiveTrue(String organizationId, String userId);

    List<ConnectorTokenDocument> findAllByOrganizationId(String organizationId);
}

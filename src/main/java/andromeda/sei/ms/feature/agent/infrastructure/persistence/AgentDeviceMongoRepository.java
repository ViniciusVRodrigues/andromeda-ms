package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AgentDeviceMongoRepository extends MongoRepository<AgentDeviceDocument, String> {

    Optional<AgentDeviceDocument> findByIdAndOrganizationId(String id, String organizationId);

    Optional<AgentDeviceDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    Optional<AgentDeviceDocument> findByAccessTokenHash(String accessTokenHash);

    Optional<AgentDeviceDocument> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AgentDeviceDocument> findByPreviousRefreshHash(String previousRefreshHash);

    List<AgentDeviceDocument> findAllByOrganizationIdAndUserId(String organizationId, String userId);
}

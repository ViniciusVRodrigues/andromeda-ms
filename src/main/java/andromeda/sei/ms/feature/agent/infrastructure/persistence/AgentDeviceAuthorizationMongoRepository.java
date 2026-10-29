package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AgentDeviceAuthorizationMongoRepository
        extends MongoRepository<AgentDeviceAuthorizationDocument, String> {

    Optional<AgentDeviceAuthorizationDocument> findByDeviceCodeHash(String deviceCodeHash);

    Optional<AgentDeviceAuthorizationDocument> findByUserCodeHash(String userCodeHash);
}

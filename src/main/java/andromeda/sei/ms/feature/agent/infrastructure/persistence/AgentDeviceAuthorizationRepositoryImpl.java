package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AgentDeviceAuthorizationRepositoryImpl implements AgentDeviceAuthorizationRepository {

    private final AgentDeviceAuthorizationMongoRepository mongoRepository;
    private final AgentDocumentMapper mapper;

    @Override
    public AgentDeviceAuthorization save(AgentDeviceAuthorization authorization) {
        return mapper.toDomain(mongoRepository.save(mapper.toDocument(authorization)));
    }

    @Override
    public Optional<AgentDeviceAuthorization> findByDeviceCodeHash(String deviceCodeHash) {
        return mongoRepository.findByDeviceCodeHash(deviceCodeHash).map(mapper::toDomain);
    }

    @Override
    public Optional<AgentDeviceAuthorization> findByUserCodeHash(String userCodeHash) {
        return mongoRepository.findByUserCodeHash(userCodeHash).map(mapper::toDomain);
    }
}

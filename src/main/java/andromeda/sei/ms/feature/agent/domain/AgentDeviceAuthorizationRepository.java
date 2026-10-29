package andromeda.sei.ms.feature.agent.domain;

import java.util.Optional;

public interface AgentDeviceAuthorizationRepository {

    AgentDeviceAuthorization save(AgentDeviceAuthorization authorization);

    Optional<AgentDeviceAuthorization> findByDeviceCodeHash(String deviceCodeHash);

    Optional<AgentDeviceAuthorization> findByUserCodeHash(String userCodeHash);
}

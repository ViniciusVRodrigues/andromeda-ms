package andromeda.sei.ms.feature.agent.domain;

/** Who an agent request acts as: the device, its owner and the owner's organization. */
public record AgentPrincipal(String organizationId, String userId, String deviceId) {
}

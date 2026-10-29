package andromeda.sei.ms.feature.agent.application.event;

/** Published after the first local agent device of an organization is approved (agente-endpoint-v2, design 14.5). */
public record FirstAgentDeviceApproved(String organizationId) {}

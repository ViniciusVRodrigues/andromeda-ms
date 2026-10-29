package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** The machine description sent by the agent is invalid (HTTP 400). */
public class AgentDeviceInfoInvalidException extends DomainException {

    public AgentDeviceInfoInvalidException(String field) {
        super("AGENT_DEVICE_INVALID", "Campo inválido: " + field);
    }
}

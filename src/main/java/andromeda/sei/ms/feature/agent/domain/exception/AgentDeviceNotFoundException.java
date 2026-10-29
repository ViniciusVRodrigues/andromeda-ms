package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

/** Device that does not exist, belongs to another person or to another organization. */
public class AgentDeviceNotFoundException extends ResourceNotFoundException {

    public AgentDeviceNotFoundException(String id) {
        super("AGENT_DEVICE_NOT_FOUND", "Dispositivo não encontrado: " + id);
    }
}

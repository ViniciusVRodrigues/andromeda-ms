package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import org.springframework.stereotype.Component;

/** {@code andromeda task --clear}: forgets the active task of the device. What was allocated stays. */
@Component
public class ClearActiveTaskUseCase {

    private final AgentDeviceRepository devices;

    public ClearActiveTaskUseCase(AgentDeviceRepository devices) {
        this.devices = devices;
    }

    public void execute(AgentPrincipal principal) {
        devices.setActiveTask(principal.deviceId(), principal.organizationId(), null);
    }
}

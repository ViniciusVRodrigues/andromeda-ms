package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Soft-revokes a device of the caller; another person, another organization or an already revoked one is "not found". */
@Component
public class RevokeOwnDeviceUseCase {

    private final AgentDeviceRepository devices;
    private final Clock clock;

    @Autowired
    public RevokeOwnDeviceUseCase(AgentDeviceRepository devices) {
        this(devices, Clock.systemUTC());
    }

    RevokeOwnDeviceUseCase(AgentDeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    public void execute(String organizationId, String userId, String deviceId) {
        AgentDevice device = devices.findByIdAndOrganizationId(deviceId, organizationId)
                .filter(d -> userId.equals(d.getUserId()) && !d.isRevoked())
                .orElseThrow(() -> new AgentDeviceNotFoundException(deviceId));
        Instant now = clock.instant();
        device.revoke(userId, now);
        device.setUpdatedAt(now);
        devices.save(device);
    }
}

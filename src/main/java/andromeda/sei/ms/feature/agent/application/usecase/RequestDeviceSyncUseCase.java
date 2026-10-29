package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.SyncRequestOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Asks a device to sync now. Another person's device (without the manage-all permission), another
 * organization's and a revoked one are "not found". A pending request is returned as is (idempotent).
 */
@Component
public class RequestDeviceSyncUseCase {

    private final AgentDeviceRepository devices;
    private final Clock clock;

    @Autowired
    public RequestDeviceSyncUseCase(AgentDeviceRepository devices) {
        this(devices, Clock.systemUTC());
    }

    RequestDeviceSyncUseCase(AgentDeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    public SyncRequestOutput execute(String organizationId, String userId, boolean canManageAll, String deviceId) {
        AgentDevice device = find(organizationId, userId, canManageAll, deviceId);
        if (device.getSyncRequestedAt() != null) {
            return output(device);
        }
        // Millisecond precision: the agent echoes it back and the match is exact.
        Instant at = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        devices.requestSync(deviceId, organizationId, at, userId);
        // Whether this call or a concurrent one wrote it, answer with what is stored now.
        return output(find(organizationId, userId, canManageAll, deviceId));
    }

    private AgentDevice find(String organizationId, String userId, boolean canManageAll, String deviceId) {
        return devices.findByIdAndOrganizationId(deviceId, organizationId)
                .filter(d -> !d.isRevoked() && (canManageAll || userId.equals(d.getUserId())))
                .orElseThrow(() -> new AgentDeviceNotFoundException(deviceId));
    }

    private static SyncRequestOutput output(AgentDevice d) {
        return new SyncRequestOutput(d.getId(), d.getSyncRequestedAt(), d.getSyncServedAt());
    }
}

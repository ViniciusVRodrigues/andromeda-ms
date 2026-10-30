package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Sign of life about every minute: records contact, version and backlog; never touches {@code lastUploadAt}. */
@Component
public class RecordHeartbeatUseCase {

    /** An ISO-8601 instant fits easily; anything longer is ignored, never a 400. */
    static final int MAX_SYNC_SERVED = 40;

    private final AgentDeviceRepository devices;
    private final AgentVersionPolicy versionPolicy;
    private final Clock clock;

    @Autowired
    public RecordHeartbeatUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy) {
        this(devices, versionPolicy, Clock.systemUTC());
    }

    RecordHeartbeatUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy, Clock clock) {
        this.devices = devices;
        this.versionPolicy = versionPolicy;
        this.clock = clock;
    }

    public HeartbeatOutput execute(AgentPrincipal principal, HeartbeatInput input) {
        Instant now = clock.instant();
        Integer pending = input.pendingRows() == null ? null : Math.max(0, input.pendingRows());
        String version = input.agentVersion() == null || input.agentVersion().isBlank() ? null
                : input.agentVersion().trim();
        String timezone = input.timezone() != null && ZoneId.getAvailableZoneIds().contains(input.timezone())
                ? input.timezone() : null; // invalid or absent: keeps the one of the pairing
        devices.recordHeartbeat(principal.deviceId(), principal.organizationId(), now, version, pending, timezone);

        Instant served = parseInstant(input.syncServed());
        if (served != null) {
            devices.findActiveByIdAndOrganizationId(principal.deviceId(), principal.organizationId())
                    .map(AgentDevice::getSyncRequestedAt)
                    .filter(req -> req.truncatedTo(ChronoUnit.MILLIS).equals(served.truncatedTo(ChronoUnit.MILLIS)))
                    .ifPresent(req -> devices.clearServedSync(principal.deviceId(), principal.organizationId(),
                            req, now));
        }

        AgentDevice device = devices.findActiveByIdAndOrganizationId(principal.deviceId(), principal.organizationId())
                .orElseThrow(AgentTokenInvalidException::new);
        return new HeartbeatOutput(now, versionPolicy.minVersion(), DeviceOutputs.activeTask(device),
                device.getSyncRequestedAt());
    }

    private static Instant parseInstant(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_SYNC_SERVED) return null;
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null; // never fails the heartbeat
        }
    }
}

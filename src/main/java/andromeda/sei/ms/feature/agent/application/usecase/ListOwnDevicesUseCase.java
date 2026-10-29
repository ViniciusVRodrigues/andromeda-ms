package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.OsOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.SemVer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** The caller own devices: active and stale ones, plus the ones revoked in the last 90 days. */
@Component
public class ListOwnDevicesUseCase {

    static final Duration REVOKED_VISIBLE_FOR = Duration.ofDays(90);

    private final AgentDeviceRepository devices;
    private final AgentVersionPolicy versionPolicy;
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public ListOwnDevicesUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy,
                                 @Value("${andromeda.agent.stale-after:P3D}") Duration staleAfter) {
        this(devices, versionPolicy, staleAfter, Clock.systemUTC());
    }

    ListOwnDevicesUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy, Duration staleAfter,
                          Clock clock) {
        this.devices = devices;
        this.versionPolicy = versionPolicy;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    public List<DeviceOutput> execute(String organizationId, String userId) {
        Instant now = clock.instant();
        return devices.findDevicesOfUser(organizationId, userId).stream()
                .filter(d -> !d.isRevoked() || recentlyRevoked(d, now))
                .sorted(Comparator.comparing(AgentDevice::getPairedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(d -> toOutput(d, now))
                .toList();
    }

    private static boolean recentlyRevoked(AgentDevice d, Instant now) {
        return d.getRevokedAt() != null && now.isBefore(d.getRevokedAt().plus(REVOKED_VISIBLE_FOR));
    }

    private DeviceOutput toOutput(AgentDevice d, Instant now) {
        Instant reference = d.getLastSeenAt() != null ? d.getLastSeenAt() : d.getPairedAt();
        long days = reference == null ? 0 : Math.max(0, Duration.between(reference, now).toDays());
        String status;
        if (d.isRevoked()) {
            status = "REVOKED";
        } else if (reference == null || now.isAfter(reference.plus(staleAfter))) {
            status = "STALE";
        } else {
            status = "ACTIVE";
        }
        String versionStatus = versionPolicy.statusOf(d.getAgentVersion());
        OsOutput os = new OsOutput(d.getOs() == null ? null : d.getOs().toUpperCase(), d.getOsLabel(), d.getArch());
        return new DeviceOutput(d.getId(), d.getMachineName(), os, d.getAgentVersion(), versionStatus,
                d.getPairedAt(), d.getLastSeenAt(), d.getLastUploadAt(), status, days,
                !AgentVersionPolicy.OK.equals(versionStatus), versionPolicy.minVersion(),
                DeviceOutputs.activeTask(d), d.getRevokedAt(), d.getSyncRequestedAt(), d.getSyncServedAt(),
                !d.isRevoked() && SemVer.isAtLeast(d.getAgentVersion(), AgentVersionPolicy.SYNC_NOW_MIN_VERSION));
    }
}

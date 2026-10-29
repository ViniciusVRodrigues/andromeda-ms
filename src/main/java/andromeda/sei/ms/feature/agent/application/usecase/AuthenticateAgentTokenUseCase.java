package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Resolves a Bearer access token to the device it belongs to, checks that the device, its person and
 * the organization are still active and records the contact ({@code lastSeenAt} at most once a
 * minute, plus the version header whenever it changes).
 */
@Component
public class AuthenticateAgentTokenUseCase {

    static final Duration SEEN_WRITE_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_VERSION_LENGTH = 40;

    /** {@code versionSupported} is false for a missing or old {@code X-Andromeda-Agent-Version}. */
    public record Result(AgentPrincipal principal, boolean versionSupported, String minVersion) {
    }

    private final AgentDeviceRepository devices;
    private final EffectivePermissionService permissions;
    private final AgentVersionPolicy versionPolicy;
    private final Clock clock;

    @Autowired
    public AuthenticateAgentTokenUseCase(AgentDeviceRepository devices, EffectivePermissionService permissions,
                                         AgentVersionPolicy versionPolicy) {
        this(devices, permissions, versionPolicy, Clock.systemUTC());
    }

    AuthenticateAgentTokenUseCase(AgentDeviceRepository devices, EffectivePermissionService permissions,
                                  AgentVersionPolicy versionPolicy, Clock clock) {
        this.devices = devices;
        this.permissions = permissions;
        this.versionPolicy = versionPolicy;
        this.clock = clock;
    }

    public Result execute(String rawToken, String versionHeader) {
        if (rawToken == null || !rawToken.startsWith(AgentCodes.ACCESS_TOKEN_PREFIX)) {
            throw new AgentTokenInvalidException();
        }
        Instant now = clock.instant();
        AgentDevice device = devices.findByAccessTokenHash(AgentCodes.sha256(rawToken))
                .orElseThrow(AgentTokenInvalidException::new);
        if (device.isRevoked() || device.getAccessTokenExpiresAt() == null
                || !now.isBefore(device.getAccessTokenExpiresAt())) {
            throw new AgentTokenInvalidException();
        }
        if (permissions.snapshot(device.getOrganizationId(), device.getUserId()).isEmpty()) {
            throw new AgentTokenInvalidException();
        }

        String version = cleanVersion(versionHeader);
        boolean versionChanged = version != null && !version.equals(device.getAgentVersion());
        boolean seenDue = device.getLastSeenAt() == null
                || !now.isBefore(device.getLastSeenAt().plus(SEEN_WRITE_INTERVAL));
        if (seenDue || versionChanged) {
            devices.touchSeen(device.getId(), device.getOrganizationId(), now, version);
        }

        return new Result(new AgentPrincipal(device.getOrganizationId(), device.getUserId(), device.getId()),
                versionPolicy.isSupported(version), versionPolicy.minVersion());
    }

    private static String cleanVersion(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String v = header.trim();
        return v.length() > MAX_VERSION_LENGTH ? v.substring(0, MAX_VERSION_LENGTH) : v;
    }
}

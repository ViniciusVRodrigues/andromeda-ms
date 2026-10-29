package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeOutput;
import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceInfoInvalidException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

/** Starts a pairing (RFC 8628): stores only the hashes of the device code and of the user code. */
@Component
public class RequestDeviceCodeUseCase {

    private static final Set<String> OSES = Set.of("windows", "macos", "linux");
    private static final Duration PURGE_AFTER_EXPIRY = Duration.ofDays(1);

    private final AgentDeviceAuthorizationRepository authorizations;
    private final AgentVersionPolicy versionPolicy;
    private final Duration ttl;
    private final int pollInterval;
    private final Clock clock;

    @Autowired
    public RequestDeviceCodeUseCase(AgentDeviceAuthorizationRepository authorizations,
                                    AgentVersionPolicy versionPolicy,
                                    @Value("${andromeda.agent.device-code-ttl:PT10M}") Duration ttl,
                                    @Value("${andromeda.agent.poll-interval:5}") int pollInterval) {
        this(authorizations, versionPolicy, ttl, pollInterval, Clock.systemUTC());
    }

    RequestDeviceCodeUseCase(AgentDeviceAuthorizationRepository authorizations, AgentVersionPolicy versionPolicy,
                             Duration ttl, int pollInterval, Clock clock) {
        this.authorizations = authorizations;
        this.versionPolicy = versionPolicy;
        this.ttl = ttl;
        this.pollInterval = pollInterval;
        this.clock = clock;
    }

    public DeviceCodeOutput execute(DeviceCodeInput input) {
        validate(input);
        versionPolicy.requireSupported(input.agentVersion());

        String deviceCode = AgentCodes.newDeviceCode();
        String userCode = AgentCodes.newUserCode();
        Instant now = clock.instant();

        AgentDeviceAuthorization authorization = new AgentDeviceAuthorization();
        authorization.setDeviceCodeHash(AgentCodes.sha256(deviceCode));
        authorization.setUserCodeHash(AgentCodes.sha256(userCode));
        authorization.setMachineName(input.machineName().trim());
        authorization.setOs(input.os());
        authorization.setOsVersion(input.osVersion());
        authorization.setOsLabel(input.osLabel());
        authorization.setArch(input.arch());
        authorization.setTimezone(input.timezone());
        authorization.setAgentVersion(input.agentVersion());
        authorization.setIp(input.ip());
        authorization.setStatus(AgentDeviceAuthorization.Status.PENDING);
        authorization.setIntervalSeconds(pollInterval);
        authorization.setExpiresAt(now.plus(ttl));
        authorization.setPurgeAt(now.plus(ttl).plus(PURGE_AFTER_EXPIRY));
        authorization.setActive(true);
        authorization.setCreatedAt(now);
        authorization.setUpdatedAt(now);
        authorizations.save(authorization);

        return new DeviceCodeOutput(deviceCode, AgentCodes.formatUserCode(userCode), ttl.toSeconds(), pollInterval);
    }

    private static void validate(DeviceCodeInput in) {
        if (in.machineName() == null || in.machineName().isBlank() || in.machineName().trim().length() > 64) {
            throw new AgentDeviceInfoInvalidException("machineName");
        }
        if (in.os() == null || !OSES.contains(in.os())) {
            throw new AgentDeviceInfoInvalidException("os");
        }
        if (in.osVersion() != null && in.osVersion().length() > 64) {
            throw new AgentDeviceInfoInvalidException("osVersion");
        }
        if (in.osLabel() != null && in.osLabel().length() > 64) {
            throw new AgentDeviceInfoInvalidException("osLabel");
        }
        if (in.arch() != null && in.arch().length() > 16) {
            throw new AgentDeviceInfoInvalidException("arch");
        }
        if (in.timezone() == null || !ZoneId.getAvailableZoneIds().contains(in.timezone())) {
            throw new AgentDeviceInfoInvalidException("timezone");
        }
    }
}

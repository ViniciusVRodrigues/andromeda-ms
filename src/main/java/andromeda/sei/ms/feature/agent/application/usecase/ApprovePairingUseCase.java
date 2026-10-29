package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ApprovedDeviceOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The logged-in person approves the pairing: creates the {@link AgentDevice} in their organization.
 * Idempotent for the same person; any other state answers 409.
 */
@Component
public class ApprovePairingUseCase {

    private final AgentDeviceAuthorizationRepository authorizations;
    private final AgentDeviceRepository devices;
    private final Clock clock;

    @Autowired
    public ApprovePairingUseCase(AgentDeviceAuthorizationRepository authorizations, AgentDeviceRepository devices) {
        this(authorizations, devices, Clock.systemUTC());
    }

    ApprovePairingUseCase(AgentDeviceAuthorizationRepository authorizations, AgentDeviceRepository devices,
                          Clock clock) {
        this.authorizations = authorizations;
        this.devices = devices;
        this.clock = clock;
    }

    public ApprovedDeviceOutput execute(String organizationId, String userId, String typedUserCode) {
        Instant now = clock.instant();
        AgentDeviceAuthorization authorization = PendingPairings.find(authorizations, typedUserCode);
        String status = PendingPairings.statusOf(authorization, now);

        if (PendingPairings.APPROVED.equals(status)) {
            return alreadyApprovedBy(authorization, organizationId, userId);
        }
        if (!PendingPairings.PENDING.equals(status)) {
            throw PendingPairings.conflictFor(status);
        }

        AgentDevice device = new AgentDevice();
        device.setOrganizationId(organizationId);
        device.setUserId(userId);
        device.setMachineName(authorization.getMachineName());
        device.setOs(authorization.getOs());
        device.setOsVersion(authorization.getOsVersion());
        device.setOsLabel(authorization.getOsLabel());
        device.setArch(authorization.getArch());
        device.setTimezone(authorization.getTimezone());
        device.setAgentVersion(authorization.getAgentVersion());
        device.setPairedFromIp(authorization.getIp());
        device.setPairedAt(now);
        device.setCutoverDay(cutoverDay(authorization.getTimezone(), now));
        device.setActive(true);
        device.setCreatedAt(now);
        device.setUpdatedAt(now);
        AgentDevice saved = devices.save(device);

        authorization.setStatus(AgentDeviceAuthorization.Status.APPROVED);
        authorization.setOrganizationId(organizationId);
        authorization.setUserId(userId);
        authorization.setDeviceId(saved.getId());
        authorization.setUpdatedAt(now);
        try {
            authorizations.save(authorization);
        } catch (OptimisticLockingFailureException e) {
            // two decisions raced and the other won: this device must not stay behind
            saved.revoke(userId, now);
            devices.save(saved);
            throw AgentPairingConflictException.used();
        }
        return new ApprovedDeviceOutput(saved.getId(), saved.getMachineName());
    }

    private ApprovedDeviceOutput alreadyApprovedBy(AgentDeviceAuthorization a, String organizationId, String userId) {
        if (!organizationId.equals(a.getOrganizationId()) || !userId.equals(a.getUserId()) || a.getDeviceId() == null) {
            throw AgentPairingConflictException.used();
        }
        return devices.findActiveByIdAndOrganizationId(a.getDeviceId(), organizationId)
                .map(d -> new ApprovedDeviceOutput(d.getId(), d.getMachineName()))
                .orElseThrow(AgentPairingConflictException::used);
    }

    /**
     * Extension point for the AIEF cutover (package 4): the first day the agent counts for. Today it is
     * the local day of the approval in the time zone of the device.
     */
    LocalDate cutoverDay(String timezone, Instant approvedAt) {
        return approvedAt.atZone(ZoneId.of(timezone)).toLocalDate();
    }
}

package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Denies a pending pairing. Idempotent when already denied; any other state answers 409. */
@Component
public class DenyPairingUseCase {

    private final AgentDeviceAuthorizationRepository authorizations;
    private final Clock clock;

    @Autowired
    public DenyPairingUseCase(AgentDeviceAuthorizationRepository authorizations) {
        this(authorizations, Clock.systemUTC());
    }

    DenyPairingUseCase(AgentDeviceAuthorizationRepository authorizations, Clock clock) {
        this.authorizations = authorizations;
        this.clock = clock;
    }

    public void execute(String organizationId, String userId, String typedUserCode) {
        Instant now = clock.instant();
        AgentDeviceAuthorization a = PendingPairings.find(authorizations, typedUserCode);
        String status = PendingPairings.statusOf(a, now);
        if (PendingPairings.DENIED.equals(status)) {
            return;
        }
        if (!PendingPairings.PENDING.equals(status)) {
            throw PendingPairings.conflictFor(status);
        }
        a.setStatus(AgentDeviceAuthorization.Status.DENIED);
        a.setOrganizationId(organizationId);
        a.setUserId(userId);
        a.setUpdatedAt(now);
        authorizations.save(a);
    }
}

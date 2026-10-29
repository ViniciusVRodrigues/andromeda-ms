package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentCodes;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization.Status;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingConflictException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingNotFoundException;

import java.time.Instant;

/** Lookup of a pairing by what the person typed, and its status as the screens see it. */
final class PendingPairings {

    static final String PENDING = "PENDING";
    static final String APPROVED = "APPROVED";
    static final String DENIED = "DENIED";
    static final String EXPIRED = "EXPIRED";
    static final String USED = "USED";

    private PendingPairings() {
    }

    /** Any state; only a code that does not exist (or was purged) is "not found". */
    static AgentDeviceAuthorization find(AgentDeviceAuthorizationRepository repository, String typedUserCode) {
        String normalized = AgentCodes.normalizeUserCode(typedUserCode)
                .orElseThrow(AgentPairingNotFoundException::new);
        return repository.findByUserCodeHash(AgentCodes.sha256(normalized))
                .orElseThrow(AgentPairingNotFoundException::new);
    }

    static String statusOf(AgentDeviceAuthorization a, Instant now) {
        if (a.getStatus() == Status.CONSUMED) return USED;
        if (a.getStatus() == Status.DENIED) return DENIED;
        if (a.getStatus() == Status.APPROVED) return APPROVED;
        return a.isExpired(now) ? EXPIRED : PENDING;
    }

    /** The 409 for a state that does not allow approving or denying. */
    static AgentPairingConflictException conflictFor(String status) {
        return switch (status) {
            case EXPIRED -> AgentPairingConflictException.expired();
            case DENIED -> AgentPairingConflictException.denied();
            default -> AgentPairingConflictException.used();
        };
    }
}

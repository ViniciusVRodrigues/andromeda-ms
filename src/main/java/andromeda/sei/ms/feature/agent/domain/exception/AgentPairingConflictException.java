package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * The pairing exists but is not in a state that allows the decision (HTTP 409). {@code code} is
 * AGENT_PAIRING_EXPIRED, AGENT_PAIRING_USED or AGENT_PAIRING_DENIED and {@code pairingStatus} goes in the body.
 */
public class AgentPairingConflictException extends ResourceConflictException {

    private final String pairingStatus;

    private AgentPairingConflictException(String code, String message, String pairingStatus) {
        super(code, message);
        this.pairingStatus = pairingStatus;
    }

    public static AgentPairingConflictException expired() {
        return new AgentPairingConflictException("AGENT_PAIRING_EXPIRED", "O código de pareamento expirou", "EXPIRED");
    }

    public static AgentPairingConflictException used() {
        return new AgentPairingConflictException("AGENT_PAIRING_USED", "O código de pareamento já foi usado", "USED");
    }

    public static AgentPairingConflictException denied() {
        return new AgentPairingConflictException("AGENT_PAIRING_DENIED", "O pareamento foi negado", "DENIED");
    }

    public String getPairingStatus() {
        return pairingStatus;
    }
}

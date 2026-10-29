package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

/** Unknown, expired, denied or already used pairing code (never says which). */
public class AgentPairingNotFoundException extends ResourceNotFoundException {

    public AgentPairingNotFoundException() {
        super("AGENT_PAIRING_NOT_FOUND", "Código de pareamento não encontrado ou expirado");
    }
}

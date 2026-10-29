package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** Only rows of an AGENT allocation can be moved this way (HTTP 422). */
public class AiAllocationNotAgentException extends BusinessRuleException {

    public AiAllocationNotAgentException() {
        super("AI_ALLOCATION_NOT_AGENT", "Só o uso apontado pelo agente pode ser movido por aqui");
    }
}

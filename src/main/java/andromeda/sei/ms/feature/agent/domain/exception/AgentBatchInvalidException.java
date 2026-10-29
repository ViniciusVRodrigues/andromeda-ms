package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** The whole upload batch is malformed: no batchId UUID, no rows, invalid JSON (HTTP 400). */
public class AgentBatchInvalidException extends DomainException {

    public AgentBatchInvalidException(String detail) {
        super("AGENT_BATCH_INVALID", "Lote inválido: " + detail);
    }
}

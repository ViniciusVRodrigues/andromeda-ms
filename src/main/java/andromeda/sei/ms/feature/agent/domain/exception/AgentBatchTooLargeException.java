package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** The upload batch has more rows than allowed or the body is over 1 MiB (HTTP 413). */
public class AgentBatchTooLargeException extends DomainException {

    private final int maxRows;

    public AgentBatchTooLargeException(int maxRows) {
        super("AGENT_BATCH_TOO_LARGE", "Lote grande demais; envie até " + maxRows + " linhas e 1 MiB por lote.");
        this.maxRows = maxRows;
    }

    public int getMaxRows() {
        return maxRows;
    }
}

package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** The same batch is still being processed by an earlier request (HTTP 409 + {@code Retry-After}, agt10). */
public class AgentBatchInProgressException extends ResourceConflictException {

    private final long retryAfterSeconds;

    public AgentBatchInProgressException(long retryAfterSeconds) {
        super("AGENT_BATCH_IN_PROGRESS", "Este lote ainda está sendo processado; envie de novo em instantes.");
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** Too many pairing requests, lookups or token calls (HTTP 429 + {@code Retry-After}). */
public class AgentRateLimitedException extends TooManyRequestsException {

    public AgentRateLimitedException(long retryAfterSeconds) {
        super("AGENT_RATE_LIMITED", "Muitas tentativas; tente novamente em instantes.", retryAfterSeconds);
    }
}

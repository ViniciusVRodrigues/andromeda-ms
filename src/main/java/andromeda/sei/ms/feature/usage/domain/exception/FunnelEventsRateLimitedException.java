package andromeda.sei.ms.feature.usage.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** Anonymous funnel ingestion over its per-IP or per-funnel limit (HTTP 429 + {@code Retry-After}). */
public class FunnelEventsRateLimitedException extends TooManyRequestsException {

    public FunnelEventsRateLimitedException(long retryAfterSeconds) {
        super("FUNNEL_EVENTS_RATE_LIMITED", "Muitas requisições de telemetria; tente novamente em instantes.",
                retryAfterSeconds);
    }
}

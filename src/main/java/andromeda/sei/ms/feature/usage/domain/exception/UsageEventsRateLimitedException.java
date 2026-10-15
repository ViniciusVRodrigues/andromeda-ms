package andromeda.sei.ms.feature.usage.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** More than 120 ingestion requests from one user in 10 minutes (HTTP 429 + {@code Retry-After}). */
public class UsageEventsRateLimitedException extends TooManyRequestsException {

    public UsageEventsRateLimitedException(long retryAfterSeconds) {
        super("USAGE_EVENTS_RATE_LIMITED", "Muitas requisições de telemetria; tente novamente em instantes.",
                retryAfterSeconds);
    }
}

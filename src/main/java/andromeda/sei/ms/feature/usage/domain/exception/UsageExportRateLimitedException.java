package andromeda.sei.ms.feature.usage.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** More than 10 usage exports from one user in an hour (HTTP 429 + {@code Retry-After}). */
public class UsageExportRateLimitedException extends TooManyRequestsException {

    public UsageExportRateLimitedException(long retryAfterSeconds) {
        super("USAGE_EXPORT_RATE_LIMITED", "Muitos exports na última hora; tente novamente mais tarde.",
                retryAfterSeconds);
    }
}

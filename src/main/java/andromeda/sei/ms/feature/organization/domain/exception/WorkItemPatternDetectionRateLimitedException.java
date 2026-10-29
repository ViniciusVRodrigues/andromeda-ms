package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** More than one manual pattern detection per organization in 10 minutes (HTTP 429 + {@code Retry-After}). */
public class WorkItemPatternDetectionRateLimitedException extends TooManyRequestsException {

    public WorkItemPatternDetectionRateLimitedException(long retryAfterSeconds) {
        super("WORK_ITEM_PATTERN_DETECTION_RATE_LIMITED",
                "A detecção de padrões já rodou há pouco; tente novamente em alguns minutos.", retryAfterSeconds);
    }
}

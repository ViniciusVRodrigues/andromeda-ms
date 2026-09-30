package andromeda.sei.ms.feature.azure.infrastructure.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Retry policy shared by every Azure DevOps client (azure-repos-provider D1): up to
 * {@link #MAX_RETRIES} retries on 429 and 5xx, waiting what the service asks for when it says so.
 *
 * <p>Azure DevOps throttles by TSTUs and signals it with {@code Retry-After} (seconds, RFC 6585)
 * and {@code X-RateLimit-Reset} (Unix epoch seconds). The wait is the first of them present,
 * capped at {@link #MAX_WAIT}; without either header it falls back to exponential backoff
 * ({@code 2s, 4s, 8s}), which is what the Boards client used before.
 */
public final class AzureRetryPolicy {

    public static final String RETRY_AFTER = HttpHeaders.RETRY_AFTER;
    public static final String RATE_LIMIT_RESET = "X-RateLimit-Reset";
    static final int MAX_RETRIES = 3;
    static final Duration BASE_BACKOFF = Duration.ofSeconds(2);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(8);
    static final Duration MAX_WAIT = Duration.ofSeconds(60);
    private static final int TOO_MANY_REQUESTS = 429;

    private AzureRetryPolicy() {}

    public static Retry retrySpec() {
        return retrySpec(BASE_BACKOFF);
    }

    /** @param baseBackoff first backoff step; tests pass a tiny value to stay fast */
    static Retry retrySpec(Duration baseBackoff) {
        return Retry.from(signals -> signals.concatMap(signal -> {
            Throwable failure = signal.failure();
            if (!isRetryable(failure) || signal.totalRetries() >= MAX_RETRIES) {
                return Mono.error(failure);
            }
            Duration wait = serverRequestedWait((WebClientResponseException) failure, Instant.now())
                    .orElseGet(() -> backoff(baseBackoff, signal.totalRetries()));
            return Mono.delay(wait).thenReturn(signal.totalRetries());
        }));
    }

    static boolean isRetryable(Throwable failure) {
        if (!(failure instanceof WebClientResponseException ex)) return false;
        return ex.getStatusCode().value() == TOO_MANY_REQUESTS || ex.getStatusCode().is5xxServerError();
    }

    /** The wait the service asked for, if any: {@code Retry-After} first, then {@code X-RateLimit-Reset}. */
    static Optional<Duration> serverRequestedWait(WebClientResponseException ex, Instant now) {
        return serverRequestedWait(ex.getHeaders(), now);
    }

    public static Optional<Duration> serverRequestedWait(HttpHeaders headers, Instant now) {
        if (headers == null) return Optional.empty();
        Optional<Duration> retryAfter = parseSeconds(headers.getFirst(RETRY_AFTER)).map(Duration::ofSeconds);
        Optional<Duration> reset = parseSeconds(headers.getFirst(RATE_LIMIT_RESET))
                .map(epoch -> Duration.between(now, Instant.ofEpochSecond(epoch)))
                .filter(d -> !d.isNegative());
        return retryAfter.or(() -> reset).map(AzureRetryPolicy::cap);
    }

    static Duration backoff(Duration base, long retriesSoFar) {
        Duration next = base.multipliedBy(1L << Math.min(retriesSoFar, 10));
        return next.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : next;
    }

    private static Duration cap(Duration wait) {
        return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
    }

    private static Optional<Long> parseSeconds(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of((long) Math.ceil(Double.parseDouble(value.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

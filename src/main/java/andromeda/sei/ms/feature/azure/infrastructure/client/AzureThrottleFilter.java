package andromeda.sei.ms.feature.azure.infrastructure.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Honors {@code Retry-After} on <em>successful</em> responses too (azure-repos-provider D1). Azure
 * DevOps sends it before it starts delaying or blocking a caller ("the response still returns HTTP
 * 200"), so the next request of the same credential waits that long instead of pushing the
 * identity into a 429.
 *
 * <p>The pause is kept per credential (the {@code Authorization} header, hashed), because TSTU
 * limits apply per identity: one organization being throttled never slows another down.
 */
public class AzureThrottleFilter implements ExchangeFilterFunction {

    private final Map<Integer, Instant> pauseUntilByCredential = new ConcurrentHashMap<>();
    private final Clock clock;

    public AzureThrottleFilter() {
        this(Clock.systemUTC());
    }

    AzureThrottleFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        int key = credentialKey(request);
        Duration wait = remainingPause(key);
        Mono<ClientResponse> exchange = next.exchange(request).doOnNext(response -> record(key, response));
        return wait.isZero() ? exchange : Mono.delay(wait).then(exchange);
    }

    Duration remainingPause(int key) {
        Instant until = pauseUntilByCredential.get(key);
        if (until == null) return Duration.ZERO;
        Duration remaining = Duration.between(clock.instant(), until);
        if (remaining.isNegative() || remaining.isZero()) {
            pauseUntilByCredential.remove(key, until);
            return Duration.ZERO;
        }
        return remaining;
    }

    private void record(int key, ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        if (headers.getFirst(AzureRetryPolicy.RETRY_AFTER) == null) return;
        Instant now = clock.instant();
        AzureRetryPolicy.serverRequestedWait(headers, now)
                .ifPresent(wait -> pauseUntilByCredential.merge(key, now.plus(wait),
                        (current, candidate) -> candidate.isAfter(current) ? candidate : current));
    }

    static int credentialKey(ClientRequest request) {
        String authorization = request.headers().getFirst(HttpHeaders.AUTHORIZATION);
        return authorization == null ? 0 : authorization.hashCode();
    }
}

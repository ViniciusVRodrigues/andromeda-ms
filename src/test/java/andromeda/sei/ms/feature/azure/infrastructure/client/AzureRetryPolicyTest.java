package andromeda.sei.ms.feature.azure.infrastructure.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Task 1.4: 429/5xx retries honor {@code Retry-After} and {@code X-RateLimit-Reset}. */
class AzureRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void shouldPreferRetryAfterSeconds() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "7");
        headers.add("X-RateLimit-Reset", String.valueOf(NOW.plusSeconds(30).getEpochSecond()));

        assertThat(AzureRetryPolicy.serverRequestedWait(headers, NOW)).contains(Duration.ofSeconds(7));
    }

    @Test
    void shouldFallBackToRateLimitResetEpoch() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Reset", String.valueOf(NOW.plusSeconds(12).getEpochSecond()));

        assertThat(AzureRetryPolicy.serverRequestedWait(headers, NOW)).contains(Duration.ofSeconds(12));
    }

    @Test
    void shouldCapServerRequestedWait() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "3600");

        assertThat(AzureRetryPolicy.serverRequestedWait(headers, NOW)).contains(AzureRetryPolicy.MAX_WAIT);
    }

    @Test
    void shouldIgnoreUnparseableOrMissingHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT");

        assertThat(AzureRetryPolicy.serverRequestedWait(headers, NOW)).isEmpty();
        assertThat(AzureRetryPolicy.serverRequestedWait(new HttpHeaders(), NOW)).isEmpty();
    }

    @Test
    void shouldRetryOnlyThrottlingAndServerErrors() {
        assertThat(AzureRetryPolicy.isRetryable(error(429))).isTrue();
        assertThat(AzureRetryPolicy.isRetryable(error(503))).isTrue();
        assertThat(AzureRetryPolicy.isRetryable(error(401))).isFalse();
        assertThat(AzureRetryPolicy.isRetryable(error(404))).isFalse();
        assertThat(AzureRetryPolicy.isRetryable(new IllegalStateException())).isFalse();
    }

    @Test
    void shouldBackOffExponentiallyWithoutHeaders() {
        assertThat(AzureRetryPolicy.backoff(Duration.ofSeconds(2), 0)).isEqualTo(Duration.ofSeconds(2));
        assertThat(AzureRetryPolicy.backoff(Duration.ofSeconds(2), 1)).isEqualTo(Duration.ofSeconds(4));
        assertThat(AzureRetryPolicy.backoff(Duration.ofSeconds(2), 2)).isEqualTo(Duration.ofSeconds(8));
        assertThat(AzureRetryPolicy.backoff(Duration.ofSeconds(2), 5)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void shouldRetryA429ThatCarriesRetryAfterAndThenSucceed() {
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            if (calls.getAndIncrement() == 0) {
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "0").build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body("\"ok\"").build());
        }).build();

        String body = client.get().uri("https://dev.azure.com/acme/_apis/projects").retrieve()
                .bodyToMono(String.class).retryWhen(AzureRetryPolicy.retrySpec(Duration.ofMillis(1))).block();

        assertThat(body).isEqualTo("\"ok\"");
        assertThat(calls).hasValue(2);
    }

    @Test
    void shouldGiveUpAfterMaxRetries() {
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
        }).build();

        assertThatThrownBy(() -> client.get().uri("https://dev.azure.com/acme/_apis/projects").retrieve()
                .bodyToMono(String.class).retryWhen(AzureRetryPolicy.retrySpec(Duration.ofMillis(1))).block())
                .isInstanceOf(WebClientResponseException.class);
        assertThat(calls).hasValue(1 + AzureRetryPolicy.MAX_RETRIES);
    }

    @Test
    void shouldNotRetryUnauthorized() {
        AtomicInteger calls = new AtomicInteger();
        WebClient client = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
        }).build();

        assertThatThrownBy(() -> client.get().uri("https://dev.azure.com/acme/_apis/projects").retrieve()
                .bodyToMono(String.class).retryWhen(AzureRetryPolicy.retrySpec(Duration.ofMillis(1))).block())
                .isInstanceOf(WebClientResponseException.Unauthorized.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void throttleFilterShouldPauseTheSameCredentialAfterRetryAfterOnSuccess() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AzureThrottleFilter filter = new AzureThrottleFilter(clock);
        List<String> seen = new ArrayList<>();
        WebClient client = WebClient.builder().filter(filter).exchangeFunction(request -> {
            seen.add(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Retry-After", "5").build());
        }).build();

        client.get().uri("https://dev.azure.com/acme/_apis/projects").header(HttpHeaders.AUTHORIZATION, "Basic a")
                .retrieve().toBodilessEntity().block();

        assertThat(filter.remainingPause("Basic a".hashCode())).isEqualTo(Duration.ofSeconds(5));
        assertThat(filter.remainingPause("Basic b".hashCode())).isZero();
        assertThat(seen).containsExactly("Basic a");
    }

    private static WebClientResponseException error(int status) {
        return WebClientResponseException.create(status, "status " + status, new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8);
    }
}

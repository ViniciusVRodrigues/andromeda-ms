package andromeda.sei.ms.feature.github.infrastructure.client;

import andromeda.sei.ms.feature.github.domain.exception.GithubPermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors {@code AzureDevOpsClientTest}'s approach: stubs {@link WebClient} at the
 * {@code ExchangeFunction} level (no MockWebServer dependency in this project).
 */
class GithubApiClientTest {

    private GithubApiClient clientFor(WebClient webClient) {
        return new GithubApiClient(webClient, new GithubResponseCache());
    }

    @Test
    void getAuthenticatedUser_shouldThrowPermissionDenied_whenGenuine403WithoutRateLimitHeader() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.FORBIDDEN)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"message\":\"Bad credentials\"}")
                                .build()))
                .build();
        GithubApiClient client = clientFor(webClient);

        assertThatThrownBy(() -> client.getAuthenticatedUser("Bearer bad-token"))
                .isInstanceOf(GithubPermissionDeniedException.class);
    }

    @Test
    void getAuthenticatedUser_shouldRetryAndSucceed_when403CarriesRateLimitRemainingZero() {
        AtomicInteger callCount = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int attempt = callCount.getAndIncrement();
                    if (attempt == 0) {
                        return Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .header("X-RateLimit-Remaining", "0")
                                .header("X-RateLimit-Reset", String.valueOf(Instant.now().getEpochSecond()))
                                .body("{\"message\":\"API rate limit exceeded\"}")
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"id\":1,\"login\":\"octocat\"}")
                            .build());
                })
                .build();
        GithubApiClient client = clientFor(webClient);

        GhUserDto user = client.getAuthenticatedUser("Bearer token");

        assertThat(user.login()).isEqualTo("octocat");
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void fetchPage_shouldDetectSecondaryRateLimitFromBodyMessageAndHonorRetryAfter() {
        // Retry-After: 1 keeps the test fast while still exercising the secondary-rate-limit
        // code path (which otherwise defaults to a >=60s wait -- unsuitable for a unit test).
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.FORBIDDEN)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .header("Retry-After", "1")
                                .body("{\"message\":\"You have exceeded a secondary rate limit. Please wait a few minutes.\"}")
                                .build()))
                .build();
        GithubApiClient client = new GithubApiClient(webClient, new GithubResponseCache(), java.time.Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.getCommits("octo", "hello", "Bearer token", null))
                .isInstanceOf(andromeda.sei.ms.feature.github.domain.exception.GithubSecondaryRateLimitException.class);
    }

    @Test
    void getOrgRepos_shouldStopPagingOnceAllPageItemsOlderThanWatermark() {
        Instant watermark = Instant.parse("2024-06-01T00:00:00Z");
        String olderPageJson = """
                [ { "id": 1, "name": "repo-a", "full_name": "org/repo-a",
                    "owner": {"login":"org"}, "updated_at": "2024-01-01T00:00:00Z" } ]
                """;

        AtomicInteger callCount = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    callCount.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            // No Link header -> pagination would stop naturally regardless, but
                            // watermark logic must also avoid requesting a nonexistent next page.
                            .body(olderPageJson)
                            .build());
                })
                .build();
        GithubApiClient client = clientFor(webClient);

        List<GhRepoDto> repos = client.getOrgRepos("org", "Bearer token", watermark);

        assertThat(repos).hasSize(1);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void fetchPage_shouldReuseCachedBodyOn304Response() {
        String firstBody = """
                [ { "sha": "abc123", "commit": { "message": "msg", "author": {"name":"a","email":"e","date":"2024-01-01T00:00:00Z"} }, "author": {"login":"octocat"} } ]
                """;
        AtomicInteger callCount = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int attempt = callCount.getAndIncrement();
                    if (attempt == 0) {
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .header("ETag", "\"etag-1\"")
                                .body(firstBody)
                                .build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.NOT_MODIFIED).build());
                })
                .build();
        GithubApiClient client = clientFor(webClient);

        List<GhCommitDto> first = client.getCommits("octo", "hello", "Bearer token", null);
        List<GhCommitDto> second = client.getCommits("octo", "hello", "Bearer token", null);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).sha()).isEqualTo("abc123");
    }
}

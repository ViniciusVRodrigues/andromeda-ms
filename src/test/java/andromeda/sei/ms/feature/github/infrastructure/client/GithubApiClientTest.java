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

    @Test
    void getCommits_shouldSendTheBranchFullyEncodedAsSha() {
        List<String> urls = new java.util.ArrayList<>();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            urls.add(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body("[]").build());
        }).build();

        clientFor(webClient).getCommits("octo", "hello", "Bearer t", "feature/a&b#1+x", Instant.parse("2024-01-01T00:00:00Z"));

        assertThat(urls).singleElement().satisfies(url -> {
            assertThat(url).contains("/repos/octo/hello/commits?per_page=100&sha=feature%2Fa%26b%231%2Bx");
            assertThat(url).contains("&since=2024-01-01T00:00:00Z");
        });
    }

    @Test
    void getCommits_shouldOmitShaWhenNoBranch() {
        List<String> urls = new java.util.ArrayList<>();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            urls.add(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body("[]").build());
        }).build();

        clientFor(webClient).getCommits("octo", "hello", "Bearer t", null);

        assertThat(urls.getFirst()).doesNotContain("sha=");
    }

    @Test
    void getBranches_shouldFollowTheLinkHeaderAndStopAtTheLimit() {
        AtomicInteger calls = new AtomicInteger();
        WebClient webClient = WebClient.builder().exchangeFunction(request -> {
            int n = calls.incrementAndGet();
            ClientResponse.Builder b = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Link", "<https://api.github.com/repos/octo/hello/branches?per_page=100&page=" + (n + 1)
                            + ">; rel=\"next\"");
            return Mono.just(b.body("[{\"name\":\"a" + n + "\"},{\"name\":\"b" + n + "\"}]").build());
        }).build();

        List<GhBranchDto> branches = clientFor(webClient).getBranches("octo", "hello", "Bearer t", 3);

        assertThat(branches).extracting(GhBranchDto::name).containsExactly("a1", "b1", "a2");
        assertThat(calls.get()).isEqualTo(2);
    }

    private WebClient jsonClient(String body) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();
    }

    /** metrics-correctness 3.x: the new fields decode through the real codec, not only via constructors. */
    @Test
    void getPullRequests_shouldDecodeDraftBaseBranchAndLabels() {
        String page = """
                [ { "id": 7, "number": 7, "title": "Revert x", "state": "closed", "draft": true,
                    "user": { "login": "octocat", "id": 1 },
                    "created_at": "2024-07-01T00:00:00Z", "updated_at": "2024-07-02T00:00:00Z",
                    "merged_at": "2024-07-02T00:00:00Z", "closed_at": "2024-07-02T00:00:00Z",
                    "head": { "ref": "hotfix/x" }, "base": { "ref": "main" },
                    "labels": [ { "name": "bug" }, { "name": "urgent" } ] } ]
                """;

        List<GhPullRequestDto> prs = clientFor(jsonClient(page)).getPullRequests("octo", "hello", "Bearer t", null);

        GhPullRequestDto pr = prs.getFirst();
        assertThat(pr.draft()).isTrue();
        assertThat(pr.base().ref()).isEqualTo("main");
        assertThat(pr.head().ref()).isEqualTo("hotfix/x");
        assertThat(pr.labels()).extracting(GhPullRequestDto.LabelDto::name).containsExactly("bug", "urgent");
    }

    @Test
    void getReviews_shouldDecodeReviewerTypeAsBot() {
        String page = """
                [ { "id": 1, "state": "COMMENTED", "submitted_at": "2024-07-01T00:00:00Z",
                    "user": { "login": "ci-app", "id": 9, "type": "Bot" } },
                  { "id": 2, "state": "APPROVED", "submitted_at": "2024-07-01T01:00:00Z",
                    "user": { "login": "alice", "id": 3, "type": "User" } } ]
                """;

        List<GhReviewDto> reviews = clientFor(jsonClient(page)).getReviews("octo", "hello", 7, "Bearer t");

        assertThat(reviews).extracting(r -> r.user().isBot()).containsExactly(true, false);
    }

    @Test
    void getRepo_shouldDecodeDefaultBranch() {
        String body = """
                { "id": 5, "name": "hello", "full_name": "octo/hello", "owner": { "login": "octo" },
                  "updated_at": "2024-07-01T00:00:00Z", "default_branch": "trunk" }
                """;

        GhRepoDto repo = clientFor(jsonClient(body)).getRepo("octo", "hello", "Bearer t");

        assertThat(repo.defaultBranch()).isEqualTo("trunk");
    }

    @Test
    void getPullRequestsCreatedSince_shouldSortByCreationKeepOnlyWindowAndStopAtFirstPageFullyOutside() {
        Instant cutoff = Instant.parse("2024-06-01T00:00:00Z");
        String firstPage = """
                [ { "id": 1, "number": 1, "created_at": "2024-07-01T00:00:00Z", "updated_at": "2024-07-02T00:00:00Z" },
                  { "id": 2, "number": 2, "created_at": "2024-05-01T00:00:00Z", "updated_at": "2024-07-03T00:00:00Z" } ]
                """;
        String secondPage = """
                [ { "id": 3, "number": 3, "created_at": "2024-04-01T00:00:00Z", "updated_at": "2024-04-02T00:00:00Z" } ]
                """;
        List<String> requestedUrls = new java.util.ArrayList<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestedUrls.add(request.url().toString());
                    boolean first = requestedUrls.size() == 1;
                    ClientResponse.Builder response = ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(first ? firstPage : secondPage);
                    if (first) {
                        response.header("Link", "<https://api.github.com/repos/octo/hello/pulls?page=2>; rel=\"next\"");
                    } else {
                        response.header("Link", "<https://api.github.com/repos/octo/hello/pulls?page=3>; rel=\"next\"");
                    }
                    return Mono.just(response.build());
                })
                .build();
        GithubApiClient client = clientFor(webClient);

        List<GhPullRequestDto> prs = client.getPullRequestsCreatedSince("octo", "hello", "Bearer token", cutoff);

        assertThat(prs).extracting(GhPullRequestDto::id).containsExactly(1L);
        assertThat(requestedUrls).hasSize(2);
        assertThat(requestedUrls.getFirst()).contains("sort=created").contains("direction=desc");
    }
}

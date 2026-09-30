package andromeda.sei.ms.feature.github.infrastructure.client;

import andromeda.sei.ms.feature.github.domain.exception.GithubPermissionDeniedException;
import andromeda.sei.ms.feature.github.domain.exception.GithubSecondaryRateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class GithubApiClient {

    private static final String BASE_URL = "https://api.github.com";
    private static final int MAX_RETRIES = 3;
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(8);
    private static final Duration DEFAULT_SECONDARY_RATE_LIMIT_MIN_WAIT = Duration.ofSeconds(60);

    private final WebClient githubWebClient;
    private final GithubResponseCache responseCache;
    private final Duration secondaryRateLimitMinWait;

    @Autowired
    public GithubApiClient(WebClient githubWebClient, GithubResponseCache responseCache) {
        this(githubWebClient, responseCache, DEFAULT_SECONDARY_RATE_LIMIT_MIN_WAIT);
    }

    /** Visible for tests -- lets {@code GithubApiClientTest} avoid a real 60s+ wait. */
    GithubApiClient(WebClient githubWebClient, GithubResponseCache responseCache, Duration secondaryRateLimitMinWait) {
        this.githubWebClient = githubWebClient;
        this.responseCache = responseCache;
        this.secondaryRateLimitMinWait = secondaryRateLimitMinWait;
    }

    // ------------------------------------------------------------------
    // Retry / rate-limit handling (design.md D4, tasks 4.2-4.6)
    // ------------------------------------------------------------------

    /**
     * Base retry shape mirrors {@code AzureDevOpsClient.retrySpec()}: up to 3 retries,
     * exponential backoff between 2s and 8s, triggered on 429 or any 5xx.
     *
     * <p>Wraps a request Mono with rate-limit-aware retry: on 403/429 with
     * {@code X-RateLimit-Remaining: 0}, waits until {@code X-RateLimit-Reset} instead of using
     * the fixed exponential schedule (task 4.3); on a secondary/abuse rate limit (task 4.5),
     * backs off using {@code Retry-After} or a fixed 60s-plus wait; a genuine 403 (task 4.4) is
     * translated to {@link GithubPermissionDeniedException} and not retried. Implemented as a
     * custom {@link Retry#from} rather than {@code Retry.backoff} since reactor-core's built-in
     * backoff spec does not expose a per-attempt custom delay function.
     */
    private <T> Mono<T> withRetry(Mono<T> mono) {
        return mono
                .onErrorMap(this::translateGenuinePermissionError)
                .retryWhen(Retry.from(retrySignals -> retrySignals.flatMap(signal -> {
                    Throwable failure = signal.failure();
                    if (signal.totalRetries() >= MAX_RETRIES) {
                        return Mono.error(failure);
                    }
                    if (!(failure instanceof WebClientResponseException ex)) {
                        return Mono.error(failure);
                    }
                    if (!isRetryable(ex)) {
                        return Mono.error(failure);
                    }
                    Duration delay = computeBackoff(ex, (int) signal.totalRetries());
                    return Mono.delay(delay);
                })));
    }

    private boolean isRetryable(Throwable throwable) {
        if (!(throwable instanceof WebClientResponseException ex)) {
            return false;
        }
        int status = ex.getStatusCode().value();
        if (status == 429) return true;
        if (ex.getStatusCode().is5xxServerError()) return true;
        if (status == 403) {
            // Retryable only if it's rate-limit exhaustion, not a genuine permission error.
            return isPrimaryRateLimitExhausted(ex) || isSecondaryRateLimit(ex);
        }
        return false;
    }

    /** task 4.4: 403 disambiguation -- rate-limit exhaustion vs genuine permission failure. */
    private boolean isPrimaryRateLimitExhausted(WebClientResponseException ex) {
        String remaining = ex.getHeaders().getFirst("X-RateLimit-Remaining");
        return "0".equals(remaining);
    }

    /** task 4.5: secondary (abuse) rate limit is signalled via response body message, not a header. */
    private boolean isSecondaryRateLimit(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body != null && body.toLowerCase().contains("secondary rate limit");
    }

    private Throwable translateGenuinePermissionError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex
                && ex.getStatusCode().value() == 403
                && !isPrimaryRateLimitExhausted(ex)
                && !isSecondaryRateLimit(ex)) {
            return new GithubPermissionDeniedException(
                    "GitHub API returned 403 without rate-limit exhaustion -- token lacks required scope or resource is inaccessible");
        }
        return throwable;
    }

    private Duration computeBackoff(WebClientResponseException ex, int attempt) {
        if (isSecondaryRateLimit(ex)) {
            return secondaryRateLimitDelay(ex);
        }
        if (isPrimaryRateLimitExhausted(ex)) {
            return primaryRateLimitDelay(ex);
        }
        // Standard exponential backoff, matching Azure's fixed schedule shape.
        long millis = Math.min(MIN_BACKOFF.toMillis() * (1L << attempt), MAX_BACKOFF.toMillis());
        return Duration.ofMillis(millis);
    }

    private Duration primaryRateLimitDelay(WebClientResponseException ex) {
        String resetHeader = ex.getHeaders().getFirst("X-RateLimit-Reset");
        if (resetHeader != null) {
            try {
                long resetEpochSeconds = Long.parseLong(resetHeader);
                long delaySeconds = resetEpochSeconds - Instant.now().getEpochSecond();
                if (delaySeconds > 0) {
                    return Duration.ofSeconds(delaySeconds);
                }
            } catch (NumberFormatException ignored) {
                // fall through to default below
            }
        }
        return MAX_BACKOFF;
    }

    private Duration secondaryRateLimitDelay(WebClientResponseException ex) {
        String retryAfter = ex.getHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter);
                return Duration.ofSeconds(Math.max(seconds, secondaryRateLimitMinWait.toSeconds()));
            } catch (NumberFormatException ignored) {
                // fall through to fixed minimum below
            }
        }
        return secondaryRateLimitMinWait;
    }

    /**
     * Thrown by callers that want to surface a hard failure when a secondary rate limit is
     * exhausted after all retries -- translates the underlying WebClientResponseException.
     */
    private Throwable translateSecondaryRateLimitExhaustion(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex && isSecondaryRateLimit(ex)) {
            return new GithubSecondaryRateLimitException(
                    "GitHub secondary (abuse) rate limit exceeded after retries: " + ex.getMessage());
        }
        return throwable;
    }

    // ------------------------------------------------------------------
    // PAT validation (task 4.7)
    // ------------------------------------------------------------------

    public GhUserDto getAuthenticatedUser(String authHeader) {
        Mono<GhUserDto> call = githubWebClient.get()
                .uri(BASE_URL + "/user")
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(GhUserDto.class);
        return withRetry(call)
                .onErrorMap(this::translateSecondaryRateLimitExhaustion)
                .block();
    }

    // ------------------------------------------------------------------
    // Repos (task 4.8) -- sort=updated&direction=desc, stop-paging on watermark
    // ------------------------------------------------------------------

    public List<GhRepoDto> getOrgRepos(String org, String authHeader, Instant since) {
        List<GhRepoDto> all = new ArrayList<>();
        String url = BASE_URL + "/orgs/" + org + "/repos?per_page=100&sort=updated&direction=desc";

        while (url != null) {
            PagedResult<GhRepoDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhRepoDto>>() {});
            all.addAll(page.items());

            if (since != null) {
                boolean allOlderThanWatermark = page.items().stream()
                        .allMatch(r -> isOlderOrEqual(r.updatedAt(), since));
                if (allOlderThanWatermark) break;
            }
            url = page.nextUrl();
        }
        return all;
    }

    /**
     * Repos visible to the PAT's user (owner, collaborator, or org membership) -- used by the
     * available-repos listing (design.md D5), NOT by sync. Follows {@code Link: rel="next"} via
     * the shared {@code fetchPage} plumbing (conditional-request caching included) but is bounded
     * to {@code maxPages} pages to cap rate-limit spend on what is a UI affordance.
     */
    public List<GhRepoDto> getUserRepos(String authHeader, int maxPages) {
        List<GhRepoDto> all = new ArrayList<>();
        String url = BASE_URL + "/user/repos?sort=updated&direction=desc&per_page=100";

        int pages = 0;
        while (url != null && pages < maxPages) {
            PagedResult<GhRepoDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhRepoDto>>() {});
            all.addAll(page.items());
            pages++;
            url = page.nextUrl();
        }
        return all;
    }

    /**
     * Fetches a single repo by owner/name -- used by {@code SyncGithubReposUseCase} to refresh
     * the one repo a {@code GithubRepoBinding} points at, since bindings are per-repo (identified
     * by "owner/name") rather than requiring a full org repo listing per sync run.
     */
    public GhRepoDto getRepo(String owner, String repoName, String authHeader) {
        Mono<GhRepoDto> call = githubWebClient.get()
                .uri(BASE_URL + "/repos/" + owner + "/" + repoName)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(GhRepoDto.class);
        return withRetry(call)
                .onErrorMap(this::translateSecondaryRateLimitExhaustion)
                .block();
    }

    /**
     * Fetches a single repo by its numeric id ({@code GET /repositories/{id}}) -- stable across
     * renames and transfers, which is why the neutral model keys GitHub repositories by it
     * (vcs-core D2).
     */
    public GhRepoDto getRepoById(String repositoryId, String authHeader) {
        Mono<GhRepoDto> call = githubWebClient.get()
                .uri(BASE_URL + "/repositories/" + repositoryId)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(GhRepoDto.class);
        return withRetry(call)
                .onErrorMap(this::translateSecondaryRateLimitExhaustion)
                .block();
    }

    // ------------------------------------------------------------------
    // Commits (task 4.9) -- native since param, follow Link to exhaustion
    // ------------------------------------------------------------------

    public List<GhCommitDto> getCommits(String owner, String repo, String authHeader, Instant since) {
        List<GhCommitDto> all = new ArrayList<>();
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/repos/" + owner + "/" + repo + "/commits?per_page=100");
        if (since != null) {
            urlBuilder.append("&since=").append(since);
        }
        String url = urlBuilder.toString();

        while (url != null) {
            PagedResult<GhCommitDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhCommitDto>>() {});
            all.addAll(page.items());
            url = page.nextUrl();
        }
        return all;
    }

    // ------------------------------------------------------------------
    // Pull requests (task 4.10) -- state=all&sort=updated&direction=desc, stop-paging
    // ------------------------------------------------------------------

    public List<GhPullRequestDto> getPullRequests(String owner, String repo, String authHeader, Instant since) {
        List<GhPullRequestDto> all = new ArrayList<>();
        String url = BASE_URL + "/repos/" + owner + "/" + repo
                + "/pulls?per_page=100&state=all&sort=updated&direction=desc";

        while (url != null) {
            PagedResult<GhPullRequestDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhPullRequestDto>>() {});
            all.addAll(page.items());

            if (since != null) {
                boolean allOlderThanWatermark = page.items().stream()
                        .allMatch(pr -> isOlderOrEqual(pr.updatedAt(), since));
                if (allOlderThanWatermark) break;
            }
            url = page.nextUrl();
        }
        return all;
    }

    /**
     * First-sync (backfill) variant: pull requests <em>created</em> at or after
     * {@code createdSince}, newest first (on-demand-refresh D6).
     *
     * <p>Uses {@code sort=created} rather than the incremental path's {@code sort=updated}:
     * under update order an old pull request touched yesterday would sit on the first page and
     * the stop condition could never be trusted. Under creation order every page after the first
     * one that is entirely older than the cutoff is older too, so paging stops there.
     */
    public List<GhPullRequestDto> getPullRequestsCreatedSince(String owner, String repo, String authHeader,
                                                              Instant createdSince) {
        List<GhPullRequestDto> all = new ArrayList<>();
        String url = BASE_URL + "/repos/" + owner + "/" + repo
                + "/pulls?per_page=100&state=all&sort=created&direction=desc";

        while (url != null) {
            PagedResult<GhPullRequestDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhPullRequestDto>>() {});
            page.items().stream()
                    .filter(pr -> !isOlderThan(pr.createdAt(), createdSince))
                    .forEach(all::add);

            boolean allOlderThanCutoff = page.items().stream()
                    .allMatch(pr -> isOlderThan(pr.createdAt(), createdSince));
            if (allOlderThanCutoff) break;
            url = page.nextUrl();
        }
        return all;
    }

    // ------------------------------------------------------------------
    // Reviews (task 4.11) -- single-PR call, standard Link pagination
    // ------------------------------------------------------------------

    public List<GhReviewDto> getReviews(String owner, String repo, int pullNumber, String authHeader) {
        List<GhReviewDto> all = new ArrayList<>();
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/reviews?per_page=100";

        while (url != null) {
            PagedResult<GhReviewDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhReviewDto>>() {});
            all.addAll(page.items());
            url = page.nextUrl();
        }
        return all;
    }


    /**
     * Commits of a single pull request. Synced commit rows carry no pull request reference,
     * and the commits endpoint reports no branch, so this cannot be derived from stored data.
     * Fetched on demand — one call when a user opens a pull request, rather than one per pull
     * request on every sync run.
     */
    public List<GhCommitDto> getPullRequestCommits(String owner, String repo, int pullNumber,
                                                    String authHeader) {
        List<GhCommitDto> all = new ArrayList<>();
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/pulls/" + pullNumber
                + "/commits?per_page=100";

        while (url != null) {
            PagedResult<GhCommitDto> page = fetchPage(url, authHeader,
                    new ParameterizedTypeReference<List<GhCommitDto>>() {});
            all.addAll(page.items());
            url = page.nextUrl();
        }
        return all;
    }

    // ------------------------------------------------------------------
    // Shared pagination + conditional-request-caching plumbing (task 4.6, 4.8-4.12)
    // ------------------------------------------------------------------

    private record PagedResult<T>(List<T> items, String nextUrl) {}

    @SuppressWarnings("unchecked")
    private <T> PagedResult<T> fetchPage(String url, String authHeader, ParameterizedTypeReference<List<T>> type) {
        GithubResponseCache.CacheEntry cached = responseCache.get(url);

        WebClient.RequestHeadersSpec<?> request = githubWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .headers(h -> {
                    if (cached != null && cached.etag() != null) {
                        h.set(HttpHeaders.IF_NONE_MATCH, cached.etag());
                    } else if (cached != null && cached.lastModified() != null) {
                        h.set(HttpHeaders.IF_MODIFIED_SINCE, cached.lastModified());
                    }
                });

        Mono<org.springframework.http.ResponseEntity<List<T>>> call = request
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 304 && cached != null) {
                        return Mono.just(org.springframework.http.ResponseEntity.ok()
                                .headers(response.headers().asHttpHeaders())
                                .body((List<T>) cached.body()));
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.toEntity(type);
                });

        org.springframework.http.ResponseEntity<List<T>> entity = withRetry(call)
                .onErrorMap(this::translateSecondaryRateLimitExhaustion)
                .block();

        List<T> items = entity != null && entity.getBody() != null ? entity.getBody() : List.of();
        HttpHeaders headers = entity != null ? entity.getHeaders() : new HttpHeaders();

        String etag = headers.getFirst(HttpHeaders.ETAG);
        String lastModified = headers.getFirst(HttpHeaders.LAST_MODIFIED);
        if (etag != null || lastModified != null) {
            responseCache.put(url, etag, lastModified, items);
        }

        String nextUrl = extractNextLink(headers.getFirst(HttpHeaders.LINK));
        return new PagedResult<>(items, nextUrl);
    }

    /**
     * Parses the {@code Link} header's {@code rel="next"} entry, GitHub's standard pagination
     * mechanism (RFC 5988), e.g. {@code <https://api.github.com/...&page=2>; rel="next"}.
     */
    private String extractNextLink(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        for (String part : linkHeader.split(",")) {
            String[] segments = part.split(";");
            if (segments.length < 2) continue;
            boolean isNext = false;
            for (int i = 1; i < segments.length; i++) {
                if (segments[i].trim().equals("rel=\"next\"")) {
                    isNext = true;
                    break;
                }
            }
            if (isNext) {
                String urlPart = segments[0].trim();
                if (urlPart.startsWith("<") && urlPart.endsWith(">")) {
                    return urlPart.substring(1, urlPart.length() - 1);
                }
            }
        }
        return null;
    }

    /** Strictly before the cutoff; an unparseable/absent timestamp is kept (never silently dropped). */
    private boolean isOlderThan(String isoTimestamp, Instant cutoff) {
        if (isoTimestamp == null) return false;
        try {
            return Instant.parse(isoTimestamp).isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isOlderOrEqual(String isoTimestamp, Instant watermark) {
        if (isoTimestamp == null) return true;
        try {
            return !Instant.parse(isoTimestamp).isAfter(watermark);
        } catch (Exception e) {
            return false;
        }
    }
}

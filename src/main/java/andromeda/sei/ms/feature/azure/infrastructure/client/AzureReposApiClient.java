package andromeda.sei.ms.feature.azure.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Azure Repos Git REST client (api-version 7.1), next to the Boards client in the Azure slice
 * (azure-repos-provider D1): same {@code azureWebClient} (logging + throttle filter), same
 * {@link AzureRetryPolicy}. Callers pass the organization URL and the complete
 * {@code Authorization} header value, so the client ignores whether it is a PAT or OAuth.
 *
 * <p>Errors surface as {@code WebClientResponseException}: 401/403 mean the credential cannot read
 * code (translated by the adapter), 404 an unknown repository. Every paged listing is bounded by
 * {@link #MAX_PAGES} so a misbehaving server cannot loop the sync forever.
 *
 * <p>Parameter names come from the Microsoft Learn reference pages (task 1.1); a spike against a
 * real organization is still pending, notably the {@code searchCriteria.fromDate} format.
 */
@Component
@RequiredArgsConstructor
public class AzureReposApiClient {

    static final String API_VERSION = "7.1";
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 100;
    static final String CONTINUATION_HEADER = "x-ms-continuationtoken";
    private static final String GIT_REPOSITORIES = "/_apis/git/repositories";
    private static final String HEADS_PREFIX = "refs/heads/";

    private final WebClient azureWebClient;

    /** Every Git repository of the organization, across ADO projects. Also the Code (Read) probe. */
    public List<AzRepositoryDto> listRepositories(String orgUrl, String authHeader) {
        URI uri = base(orgUrl, null).path(GIT_REPOSITORIES).queryParam("api-version", API_VERSION)
                .build().encode().toUri();
        return getList(uri, authHeader, new ParameterizedTypeReference<ListResponse<AzRepositoryDto>>() {});
    }

    public AzRepositoryDto getRepository(String orgUrl, String projectId, String repositoryId, String authHeader) {
        URI uri = base(orgUrl, projectId).path(GIT_REPOSITORIES + "/{repositoryId}")
                .queryParam("api-version", API_VERSION).buildAndExpand(repositoryId).encode().toUri();
        return azureWebClient.get().uri(uri).header("Authorization", authHeader).retrieve()
                .bodyToMono(AzRepositoryDto.class).retryWhen(AzureRetryPolicy.retrySpec()).block();
    }

    /** Pull requests matching the criteria, paged with {@code $top}/{@code $skip}. */
    public List<AzPullRequestDto> listPullRequests(String orgUrl, String projectId, String repositoryId,
                                                   AzPullRequestQuery query, String authHeader) {
        return pagedBySkip(skip -> {
            UriComponentsBuilder builder = repositoryPath(orgUrl, projectId, repositoryId, "/pullrequests")
                    .queryParam("searchCriteria.status", query.status());
            if (query.minTime() != null) {
                builder.queryParam("searchCriteria.minTime", query.minTime().toString())
                        .queryParam("searchCriteria.queryTimeRangeType", query.timeRangeType());
            }
            return builder.queryParam("$top", PAGE_SIZE).queryParam("$skip", skip)
                    .queryParam("api-version", API_VERSION).buildAndExpand(repositoryId).encode().toUri();
        }, authHeader, new ParameterizedTypeReference<ListResponse<AzPullRequestDto>>() {});
    }

    public List<AzThreadDto> listThreads(String orgUrl, String projectId, String repositoryId, int pullRequestId,
                                         String authHeader) {
        URI uri = repositoryPath(orgUrl, projectId, repositoryId, "/pullRequests/{pullRequestId}/threads")
                .queryParam("api-version", API_VERSION).buildAndExpand(repositoryId, pullRequestId).encode().toUri();
        return getList(uri, authHeader, new ParameterizedTypeReference<ListResponse<AzThreadDto>>() {});
    }

    public List<AzWorkItemRefDto> listPullRequestWorkItems(String orgUrl, String projectId, String repositoryId,
                                                           int pullRequestId, String authHeader) {
        URI uri = repositoryPath(orgUrl, projectId, repositoryId, "/pullRequests/{pullRequestId}/workitems")
                .queryParam("api-version", API_VERSION).buildAndExpand(repositoryId, pullRequestId).encode().toUri();
        return getList(uri, authHeader, new ParameterizedTypeReference<ListResponse<AzWorkItemRefDto>>() {});
    }

    /** Commits of a pull request, paged by the {@code x-ms-continuationtoken} response header. */
    public List<AzCommitDto> listPullRequestCommits(String orgUrl, String projectId, String repositoryId,
                                                    int pullRequestId, String authHeader) {
        List<AzCommitDto> all = new ArrayList<>();
        String continuation = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            UriComponentsBuilder builder = repositoryPath(orgUrl, projectId, repositoryId,
                    "/pullRequests/{pullRequestId}/commits").queryParam("$top", PAGE_SIZE);
            if (continuation != null) builder.queryParam("continuationToken", continuation);
            URI uri = builder.queryParam("api-version", API_VERSION)
                    .buildAndExpand(repositoryId, pullRequestId).encode().toUri();
            ResponseEntity<ListResponse<AzCommitDto>> response = azureWebClient.get().uri(uri)
                    .header("Authorization", authHeader).retrieve()
                    .toEntity(new ParameterizedTypeReference<ListResponse<AzCommitDto>>() {})
                    .retryWhen(AzureRetryPolicy.retrySpec()).block();
            if (response == null) break;
            all.addAll(valuesOf(response.getBody()));
            continuation = response.getHeaders().getFirst(CONTINUATION_HEADER);
            if (continuation == null || continuation.isBlank()) break;
        }
        return all;
    }

    /**
     * Commits of a branch authored after {@code fromDate} ({@code null} = full history), newest
     * first, paged with {@code searchCriteria.$top}/{@code searchCriteria.$skip}.
     *
     * @param branch short name or full ref; the {@code refs/heads/} prefix is stripped
     */
    public List<AzCommitDto> listCommits(String orgUrl, String projectId, String repositoryId, String branch,
                                         Instant fromDate, String authHeader) {
        return pagedBySkip(skip -> {
            UriComponentsBuilder builder = repositoryPath(orgUrl, projectId, repositoryId, "/commits");
            if (branch != null && !branch.isBlank()) {
                builder.queryParam("searchCriteria.itemVersion.version", shortBranch(branch))
                        .queryParam("searchCriteria.itemVersion.versionType", "branch");
            }
            if (fromDate != null) builder.queryParam("searchCriteria.fromDate", fromDate.toString());
            return builder.queryParam("searchCriteria.$top", PAGE_SIZE).queryParam("searchCriteria.$skip", skip)
                    .queryParam("api-version", API_VERSION).buildAndExpand(repositoryId).encode().toUri();
        }, authHeader, new ParameterizedTypeReference<ListResponse<AzCommitDto>>() {});
    }

    public static String shortBranch(String ref) {
        if (ref == null) return null;
        return ref.startsWith(HEADS_PREFIX) ? ref.substring(HEADS_PREFIX.length()) : ref;
    }

    // ------------------------------------------------------------------

    private <T> List<T> pagedBySkip(Function<Integer, URI> uriForSkip, String authHeader,
                                    ParameterizedTypeReference<ListResponse<T>> type) {
        List<T> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            List<T> values = getList(uriForSkip.apply(page * PAGE_SIZE), authHeader, type);
            all.addAll(values);
            if (values.size() < PAGE_SIZE) break;
        }
        return all;
    }

    private <T> List<T> getList(URI uri, String authHeader, ParameterizedTypeReference<ListResponse<T>> type) {
        ListResponse<T> response = azureWebClient.get().uri(uri).header("Authorization", authHeader).retrieve()
                .bodyToMono(type).retryWhen(AzureRetryPolicy.retrySpec()).block();
        return valuesOf(response);
    }

    private static <T> List<T> valuesOf(ListResponse<T> response) {
        return response == null || response.value() == null ? List.of() : response.value();
    }

    private static UriComponentsBuilder repositoryPath(String orgUrl, String projectId, String repositoryId,
                                                       String suffix) {
        return base(orgUrl, projectId).path(GIT_REPOSITORIES + "/{repositoryId}" + suffix);
    }

    /** {@code {orgUrl}[/{projectId}]}; the project segment is optional for repositories addressed by GUID. */
    private static UriComponentsBuilder base(String orgUrl, String projectId) {
        String root = orgUrl.endsWith("/") ? orgUrl.substring(0, orgUrl.length() - 1) : orgUrl;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(root);
        if (projectId != null && !projectId.isBlank()) builder.pathSegment(projectId);
        return builder;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse<T>(List<T> value, Integer count) {}
}

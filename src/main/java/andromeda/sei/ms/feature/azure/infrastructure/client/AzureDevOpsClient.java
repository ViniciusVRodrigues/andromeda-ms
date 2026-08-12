package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.WiqlFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AzureDevOpsClient {

    private static final int BATCH_SIZE = 200;
    public static final int WIQL_PAGE_SIZE = 5000;
    public static final int WIQL_MAX_PAGES = 50;
    private final WebClient azureWebClient;

    private Retry retrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(8))
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException ex) {
                        return ex.getStatusCode().value() == 429
                                || ex.getStatusCode().is5xxServerError();
                    }
                    return false;
                });
    }

    private String baseUrl(String orgUrl) {
        return orgUrl.endsWith("/") ? orgUrl.substring(0, orgUrl.length() - 1) : orgUrl;
    }

    public List<AzureProjectDto> getProjects(String orgUrl, String authHeader) {
        String url = baseUrl(orgUrl) + "/_apis/projects?api-version=7.0";

        ProjectsResponse response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(ProjectsResponse.class)
                .retryWhen(retrySpec())
                .block();
        return response != null && response.value() != null ? response.value() : List.of();
    }

    public List<AdoTeamDto> getTeams(String orgUrl, String projectId, String authHeader) {
        String url = baseUrl(orgUrl) + "/_apis/projects/" + projectId + "/teams?api-version=7.1";

        ListResponse<AdoTeamDto> response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoTeamDto>>() {})
                .retryWhen(retrySpec())
                .block();
        return response != null && response.value() != null ? response.value() : List.of();
    }

    public List<AdoIterationDto> getIterations(String orgUrl, String projectId, String teamId, String authHeader) {
        String url = baseUrl(orgUrl) + "/" + projectId + "/" + teamId
                + "/_apis/work/teamsettings/iterations?api-version=7.1";

        ListResponse<AdoIterationDto> response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoIterationDto>>() {})
                .retryWhen(retrySpec())
                .block();
        return response != null && response.value() != null ? response.value() : List.of();
    }

    public AdoProjectCapabilitiesDto getProjectCapabilities(String orgUrl, String projectId, String authHeader) {
        String url = baseUrl(orgUrl) + "/_apis/projects/" + projectId
                + "?includeCapabilities=true&api-version=7.1";

        ProjectCapabilitiesResponse response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(ProjectCapabilitiesResponse.class)
                .retryWhen(retrySpec())
                .block();

        if (response == null || response.capabilities() == null) return null;
        ProcessTemplateCapabilities pt = response.capabilities().processTemplate();
        String templateName = pt != null ? pt.templateName() : null;
        return new AdoProjectCapabilitiesDto(templateName);
    }

    /**
     * Returns the work item types defined for the given ADO project.
     * Read-only introspection call — never invoked from the sync hot path.
     */
    public List<AdoWorkItemTypeDto> getWorkItemTypes(String orgUrl, String projectId, String authHeader) {
        String url = baseUrl(orgUrl) + "/" + projectId + "/_apis/wit/workitemtypes?api-version=7.1";

        ListResponse<AdoWorkItemTypeDto> response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoWorkItemTypeDto>>() {})
                .retryWhen(retrySpec())
                .block();
        return response != null && response.value() != null ? response.value() : List.of();
    }

    /**
     * Returns the authoritative field list (reference name, name, type, required) for a
     * given work item type, regardless of whether any item currently has a value set.
     * Read-only introspection call — never invoked from the sync hot path.
     */
    public List<AdoWorkItemTypeFieldDto> getWorkItemTypeFields(
            String orgUrl, String projectId, String typeName, String authHeader) {
        String url = baseUrl(orgUrl) + "/" + projectId + "/_apis/wit/workitemtypes/" + typeName
                + "/fields?api-version=7.1";

        ListResponse<AdoWorkItemTypeFieldDto> response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoWorkItemTypeFieldDto>>() {})
                .retryWhen(retrySpec())
                .block();
        return response != null && response.value() != null ? response.value() : List.of();
    }

    /**
     * Returns the real states defined for a given work item type on the given project
     * (e.g. New, Active, Resolved, Closed for a default Bug type).
     * Read-only introspection call — never invoked from the sync hot path.
     */
    public List<AdoWorkItemTypeStateDto> getWorkItemTypeStates(
            String orgUrl, String projectId, String typeName, String authHeader) {
        String url = baseUrl(orgUrl) + "/" + projectId + "/_apis/wit/workitemtypes/" + typeName
                + "/states?api-version=7.1";

        ListResponse<AdoWorkItemTypeStateDto> response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoWorkItemTypeStateDto>>() {})
                .retryWhen(retrySpec())
                .block();
        return response != null && response.value() != null ? response.value() : List.of();
    }

    /**
     * Fetches a single page (up to {@link #WIQL_PAGE_SIZE} IDs) of work item IDs ordered
     * ascending by System.Id, applying the optional incremental-sync date filter, ID lower
     * bound (exclusive), and structured sync filter. Callers should loop, passing the
     * highest ID from the previous page as {@code lastSeenId}, until a page returns fewer
     * than {@link #WIQL_PAGE_SIZE} IDs — this keeps each individual WIQL response safely
     * under Azure DevOps's 20,000-row cap regardless of project size.
     */
    public List<Integer> queryWorkItemIdsPage(String orgUrl, String projectId, String authHeader,
                                               Instant since, Integer lastSeenId, WiqlFilter filter) {
        // $top caps the rows ADO materializes for this page. Without it a single WIQL
        // response can exceed ADO's hard 20,000-row cap, which comes back as an
        // HTTP 400 (VS403496) rather than a truncated result.
        String url = baseUrl(orgUrl) + "/" + projectId + "/_apis/wit/wiql?api-version=7.1"
                + "&$top=" + WIQL_PAGE_SIZE;

        String wiql = WiqlQueryBuilder.build(since, lastSeenId, filter);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = azureWebClient.post()
                .uri(url)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(Map.of("query", wiql)))
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(retrySpec())
                .block();

        if (response == null || response.get("workItems") == null) return List.of();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("workItems");
        return items.stream()
                .map(item -> ((Number) item.get("id")).intValue())
                .limit(WIQL_PAGE_SIZE)
                .toList();
    }

    public List<AdoWorkItemDto> getWorkItemsBatch(String orgUrl, List<Integer> ids, String authHeader) {
        if (ids.isEmpty()) return List.of();

        List<AdoWorkItemDto> all = new ArrayList<>();
        int i = 0;
        while (i < ids.size()) {
            List<Integer> batch = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
            String idsParam = batch.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            String url = baseUrl(orgUrl) + "/_apis/wit/workitems?ids=" + idsParam
                    + "&$expand=all&api-version=7.1";

            ListResponse<AdoWorkItemDto> response = azureWebClient.get()
                    .uri(url)
                    .header("Authorization", authHeader)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoWorkItemDto>>() {})
                    .retryWhen(retrySpec())
                    .block();

            if (response != null && response.value() != null) {
                all.addAll(response.value());
            }
            i += BATCH_SIZE;
        }
        return all;
    }

    public List<AdoTeamMemberDto> getTeamMembers(String orgUrl, String projectId, String teamId, String authHeader) {
        String url = baseUrl(orgUrl) + "/_apis/projects/" + projectId + "/teams/" + teamId
                + "/members?api-version=7.1";

        ListResponse<AdoTeamMemberWrapperDto> response = azureWebClient.get()
                .uri(url)
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoTeamMemberWrapperDto>>() {})
                .retryWhen(retrySpec())
                .block();

        if (response == null || response.value() == null) return List.of();
        return response.value().stream()
                .filter(w -> w.identity() != null)
                .map(w -> new AdoTeamMemberDto(w.identity().id(), w.identity().displayName(), w.identity().uniqueName()))
                .toList();
    }

    public List<AdoWorkItemUpdateDto> getWorkItemUpdates(String orgUrl, int workItemId, String authHeader) {
        List<AdoWorkItemUpdateDto> all = new ArrayList<>();
        String url = baseUrl(orgUrl) + "/_apis/wit/workitems/" + workItemId + "/updates?api-version=7.1";

        while (url != null) {
            PagedResponse<AdoWorkItemUpdateDto> response = azureWebClient.get()
                    .uri(url)
                    .header("Authorization", authHeader)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<PagedResponse<AdoWorkItemUpdateDto>>() {})
                    .retryWhen(retrySpec())
                    .block();

            if (response == null || response.value() == null) break;
            all.addAll(response.value());
            url = response.continuationToken() != null
                    ? baseUrl(orgUrl) + "/_apis/wit/workitems/" + workItemId
                            + "/updates?api-version=7.1&continuationToken=" + response.continuationToken()
                    : null;
        }
        return all;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ProjectsResponse {
        @JsonProperty("value")
        private List<AzureProjectDto> value;
        @JsonProperty("count")
        private int count;

        public List<AzureProjectDto> value() { return value; }
        public int count() { return count; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ListResponse<T> {
        @JsonProperty("value")
        private List<T> value;
        public List<T> value() { return value; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PagedResponse<T> {
        @JsonProperty("value")
        private List<T> value;
        @JsonProperty("continuationToken")
        private String continuationToken;
        public List<T> value() { return value; }
        public String continuationToken() { return continuationToken; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProjectCapabilitiesResponse(Capabilities capabilities) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Capabilities(ProcessTemplateCapabilities processTemplate) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProcessTemplateCapabilities(String templateName) {}
}

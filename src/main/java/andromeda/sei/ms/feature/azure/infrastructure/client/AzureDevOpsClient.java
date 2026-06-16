package andromeda.sei.ms.feature.azure.infrastructure.client;

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
    private final WebClient azureWebClient;

    private Retry retrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(8))
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException ex) {
                        return ex.getStatusCode().value() == 429
                                || ex.getStatusCode().is5xxServerError();
                    }
                    return true;
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

    public List<Integer> queryWorkItemIds(String orgUrl, String projectId, String authHeader, Instant since) {
        String url = baseUrl(orgUrl) + "/" + projectId + "/_apis/wit/wiql?api-version=7.1";

        String dateFilter = since != null
                ? " WHERE [System.ChangedDate] >= '" + since.toString() + "'"
                : "";
        String wiql = "SELECT [System.Id] FROM WorkItems" + dateFilter;

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

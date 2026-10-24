package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.feature.azure.domain.WiqlFilter;
import andromeda.sei.ms.feature.azure.domain.exception.AzureEntitlementsForbiddenException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

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

    /** 429/5xx retry honoring {@code Retry-After} / {@code X-RateLimit-Reset} (azure-repos-provider D1). */
    private Retry retrySpec() {
        return AzureRetryPolicy.retrySpec();
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

    public static final int TEAM_MEMBERS_PAGE_SIZE = 100;
    public static final int TEAM_MEMBERS_MAX_PAGES = 50;
    public static final int ENTITLEMENTS_MAX_PAGES = 20;
    private static final String VSAEX_HOST = "https://vsaex.dev.azure.com/";

    public List<AdoTeamMemberDto> getTeamMembers(String orgUrl, String projectId, String teamId, String authHeader) {
        List<AdoTeamMemberDto> all = new ArrayList<>();
        for (int page = 0; page < TEAM_MEMBERS_MAX_PAGES; page++) {
            int skip = page * TEAM_MEMBERS_PAGE_SIZE;
            String url = baseUrl(orgUrl) + "/_apis/projects/" + projectId + "/teams/" + teamId
                    + "/members?api-version=7.1&$top=" + TEAM_MEMBERS_PAGE_SIZE + "&$skip=" + skip;

            ListResponse<AdoTeamMemberWrapperDto> response = azureWebClient.get()
                    .uri(url)
                    .header("Authorization", authHeader)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<ListResponse<AdoTeamMemberWrapperDto>>() {})
                    .retryWhen(retrySpec())
                    .block();

            if (response == null || response.value() == null || response.value().isEmpty()) break;
            response.value().stream()
                    .filter(w -> w.identity() != null && !w.identity().isContainer())
                    .map(w -> new AdoTeamMemberDto(w.identity().id(), w.identity().displayName(),
                            w.identity().uniqueName(), false))
                    .forEach(all::add);
            if (response.value().size() < TEAM_MEMBERS_PAGE_SIZE) break;
        }
        return all;
    }

    /** Organization directory ({@code userentitlements}); only {@code subjectKind == "user"}, up to 20 pages. */
    public List<AdoUserEntitlementDto> listUserEntitlements(String orgUrl, String authHeader) {
        String org = orgName(orgUrl);
        List<AdoUserEntitlementDto> all = new ArrayList<>();
        String continuation = null;
        for (int page = 0; page < ENTITLEMENTS_MAX_PAGES; page++) {
            String url = VSAEX_HOST + org + "/_apis/userentitlements?api-version=7.1-preview.3"
                    + (continuation != null ? "&continuationToken="
                    + java.net.URLEncoder.encode(continuation, java.nio.charset.StandardCharsets.UTF_8) : "");

            EntitlementsResponse response = azureWebClient.get()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", authHeader)
                    .retrieve()
                    .onStatus(st -> st.value() == 401 || st.value() == 403,
                            r -> reactor.core.publisher.Mono.error(new AzureEntitlementsForbiddenException()))
                    .bodyToMono(EntitlementsResponse.class)
                    .retryWhen(retrySpec())
                    .block();

            if (response == null) break;
            for (EntitlementItem item : response.entries()) {
                EntitlementUser u = item.user();
                if (u == null || !"user".equalsIgnoreCase(u.subjectKind())) continue;
                all.add(new AdoUserEntitlementDto(item.id(), u.displayName(), u.mailAddress(),
                        u.principalName(), u.originId()));
            }
            continuation = response.continuationToken();
            if (continuation == null || continuation.isBlank()) break;
        }
        return all;
    }

    /** {@code https://dev.azure.com/{org}} or {@code https://{org}.visualstudio.com} to {@code {org}}. */
    private static String orgName(String orgUrl) {
        String u = AzureOrgUrl.normalize(orgUrl);
        String suffix = ".visualstudio.com";
        if (u.startsWith("https://") && u.endsWith(suffix)) {
            return u.substring("https://".length(), u.length() - suffix.length());
        }
        return u.substring(u.lastIndexOf('/') + 1);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EntitlementsResponse(List<EntitlementItem> items, List<EntitlementItem> members,
                                        String continuationToken) {
        List<EntitlementItem> entries() {
            if (items != null) return items;
            return members != null ? members : List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EntitlementItem(String id, EntitlementUser user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EntitlementUser(String subjectKind, String displayName, String mailAddress,
                                   String principalName, String originId) {}

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

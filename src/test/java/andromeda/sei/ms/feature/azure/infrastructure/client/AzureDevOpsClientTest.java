package andromeda.sei.ms.feature.azure.infrastructure.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link AzureDevOpsClient} against a stubbed {@link WebClient} built with a custom
 * {@code ExchangeFunction} rather than a real HTTP server (MockWebServer) — no such test
 * dependency exists in this project yet, and stubbing at the ExchangeFunction level is
 * sufficient to verify both request-URL construction and response deserialization without
 * adding a new build dependency.
 */
class AzureDevOpsClientTest {

    private AzureDevOpsClient clientFor(WebClient webClient) {
        return new AzureDevOpsClient(webClient);
    }

    private WebClient webClientReturning(int status, String body) {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.valueOf(status))
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body(body)
                                .build()))
                .build();
    }

    private WebClient webClientCapturingRequest(AtomicReference<ClientRequest> captured, String body) {
        return WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(
                            ClientResponse.create(HttpStatus.OK)
                                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                    .body(body)
                                    .build());
                })
                .build();
    }

    @Test
    void getWorkItemTypeFields_shouldDeserializeUsingRealAlwaysRequiredKey() {
        String json = """
                {
                  "value": [
                    { "referenceName": "System.Title", "name": "Title", "type": "string", "alwaysRequired": true },
                    { "referenceName": "Custom.Complexidade", "name": "Complexidade", "type": "integer", "alwaysRequired": false }
                  ],
                  "count": 2
                }
                """;
        AzureDevOpsClient client = clientFor(webClientReturning(200, json));

        List<AdoWorkItemTypeFieldDto> fields = client.getWorkItemTypeFields(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA==");

        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).referenceName()).isEqualTo("System.Title");
        assertThat(fields.get(0).required()).isTrue();
        assertThat(fields.get(1).referenceName()).isEqualTo("Custom.Complexidade");
        assertThat(fields.get(1).required()).isFalse();
    }

    @Test
    void getProjects_shouldBuildExpectedUrlFromWellFormedOrgUrl() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        String json = """
                { "value": [ { "id": "azure-id-1", "name": "My Project" } ], "count": 1 }
                """;
        AzureDevOpsClient client = clientFor(webClientCapturingRequest(captured, json));

        List<AzureProjectDto> projects = client.getProjects("https://dev.azure.com/myorg/", "Basic dGVzdA==");

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).id()).isEqualTo("azure-id-1");
        URI requestedUri = captured.get().url();
        // Confirms baseUrl() trims the trailing slash before appending the API path segment.
        assertThat(requestedUri.toString())
                .isEqualTo("https://dev.azure.com/myorg/_apis/projects?api-version=7.0");
    }

    @Test
    void getWorkItemTypes_shouldBuildExpectedUrlFromWellFormedOrgUrl() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        String json = """
                { "value": [ { "name": "Bug", "referenceName": "Microsoft.VSTS.WorkItemTypes.Bug" } ] }
                """;
        AzureDevOpsClient client = clientFor(webClientCapturingRequest(captured, json));

        List<AdoWorkItemTypeDto> types = client.getWorkItemTypes("https://dev.azure.com/myorg", "proj-1", "Basic dGVzdA==");

        assertThat(types).hasSize(1);
        assertThat(types.get(0).name()).isEqualTo("Bug");
        assertThat(captured.get().url().toString())
                .isEqualTo("https://dev.azure.com/myorg/proj-1/_apis/wit/workitemtypes?api-version=7.1");
    }

    @Test
    void getWorkItemsBatch_shouldDeserializeRelationsFromExpandAllResponse() {
        // Realistic shape of an Azure DevOps "$expand=all" work item response: a Task with a
        // System.LinkTypes.Hierarchy-Reverse relation pointing at its parent's work item id.
        String json = """
                {
                  "value": [
                    {
                      "id": 100,
                      "fields": {
                        "System.Title": "My Task",
                        "System.WorkItemType": "Task",
                        "System.State": "Active"
                      },
                      "relations": [
                        {
                          "rel": "System.LinkTypes.Hierarchy-Reverse",
                          "url": "https://dev.azure.com/myorg/_apis/wit/workItems/42"
                        },
                        {
                          "rel": "System.LinkTypes.Related",
                          "url": "https://dev.azure.com/myorg/_apis/wit/workItems/99"
                        }
                      ]
                    }
                  ],
                  "count": 1
                }
                """;
        AzureDevOpsClient client = clientFor(webClientReturning(200, json));

        List<AdoWorkItemDto> items = client.getWorkItemsBatch(
                "https://dev.azure.com/myorg", List.of(100), "Basic dGVzdA==");

        assertThat(items).hasSize(1);
        AdoWorkItemDto item = items.get(0);
        assertThat(item.id()).isEqualTo(100);
        assertThat(item.relations()).hasSize(2);
        assertThat(item.relations().get(0).rel()).isEqualTo("System.LinkTypes.Hierarchy-Reverse");
        assertThat(item.relations().get(0).url()).isEqualTo("https://dev.azure.com/myorg/_apis/wit/workItems/42");
    }

    @Test
    void getWorkItemTypeFields_shouldRetryOn5xxThenSucceed() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        String json = """
                { "value": [ { "referenceName": "System.Title", "name": "Title", "type": "string", "alwaysRequired": true } ] }
                """;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int current = attempt.updateAndGet(n -> n + 1);
                    if (current < 2) {
                        return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        List<AdoWorkItemTypeFieldDto> fields = client.getWorkItemTypeFields(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA==");

        assertThat(fields).hasSize(1);
        assertThat(attempt.get()).isEqualTo(2);
    }

    @Test
    void getWorkItemTypeFields_shouldRetryOn429ThenSucceed() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        String json = """
                { "value": [ { "referenceName": "System.Title", "name": "Title", "type": "string", "alwaysRequired": true } ] }
                """;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int current = attempt.updateAndGet(n -> n + 1);
                    if (current < 2) {
                        return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        List<AdoWorkItemTypeFieldDto> fields = client.getWorkItemTypeFields(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA==");

        assertThat(fields).hasSize(1);
        assertThat(attempt.get()).isEqualTo(2);
    }

    @Test
    void getWorkItemTypeFields_shouldNotRetryOnNon5xxClientError() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    attempt.updateAndGet(n -> n + 1);
                    return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        assertThatThrownBy(() -> client.getWorkItemTypeFields(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA=="))
                .isInstanceOf(WebClientResponseException.class);
        assertThat(attempt.get()).isEqualTo(1);
    }

    @Test
    void getWorkItemTypeStates_shouldDeserializeAndBuildExpectedUrl() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        String json = """
                {
                  "value": [
                    { "name": "New", "color": "b2b2b2", "category": "Proposed" },
                    { "name": "Active", "color": "007acc", "category": "InProgress" }
                  ],
                  "count": 2
                }
                """;
        AzureDevOpsClient client = clientFor(webClientCapturingRequest(captured, json));

        List<AdoWorkItemTypeStateDto> states = client.getWorkItemTypeStates(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA==");

        assertThat(states).hasSize(2);
        assertThat(states.get(0).name()).isEqualTo("New");
        assertThat(states.get(0).category()).isEqualTo("Proposed");
        assertThat(states.get(1).name()).isEqualTo("Active");
        assertThat(captured.get().url().toString())
                .isEqualTo("https://dev.azure.com/myorg/proj-1/_apis/wit/workitemtypes/Bug/states?api-version=7.1");
    }

    @Test
    void getWorkItemTypeStates_shouldRetryOn5xxThenSucceed() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        String json = """
                { "value": [ { "name": "New", "color": "b2b2b2", "category": "Proposed" } ] }
                """;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int current = attempt.updateAndGet(n -> n + 1);
                    if (current < 2) {
                        return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        List<AdoWorkItemTypeStateDto> states = client.getWorkItemTypeStates(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA==");

        assertThat(states).hasSize(1);
        assertThat(attempt.get()).isEqualTo(2);
    }

    @Test
    void getWorkItemTypeStates_shouldRetryOn429ThenSucceed() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        String json = """
                { "value": [ { "name": "New", "color": "b2b2b2", "category": "Proposed" } ] }
                """;
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    int current = attempt.updateAndGet(n -> n + 1);
                    if (current < 2) {
                        return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        List<AdoWorkItemTypeStateDto> states = client.getWorkItemTypeStates(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA==");

        assertThat(states).hasSize(1);
        assertThat(attempt.get()).isEqualTo(2);
    }

    @Test
    void getWorkItemTypeStates_shouldNotRetryOnNon5xxClientError() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    attempt.updateAndGet(n -> n + 1);
                    return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build());
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        assertThatThrownBy(() -> client.getWorkItemTypeStates(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA=="))
                .isInstanceOf(WebClientResponseException.class);
        assertThat(attempt.get()).isEqualTo(1);
    }

    @Test
    void getWorkItemTypeStates_shouldNotRetryOnNonWebClientResponseException() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    attempt.updateAndGet(n -> n + 1);
                    return Mono.error(new WebClientRequestException(
                            new RuntimeException("DNS resolution failed"),
                            org.springframework.http.HttpMethod.GET,
                            URI.create("https://dev.azure.com/myorg"),
                            org.springframework.http.HttpHeaders.EMPTY));
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        assertThatThrownBy(() -> client.getWorkItemTypeStates(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA=="))
                .isInstanceOf(WebClientRequestException.class);
        assertThat(attempt.get()).isEqualTo(1);
    }

    @Test
    void getWorkItemTypeFields_shouldNotRetryOnNonWebClientResponseException() {
        AtomicReference<Integer> attempt = new AtomicReference<>(0);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    attempt.updateAndGet(n -> n + 1);
                    return Mono.error(new WebClientRequestException(
                            new RuntimeException("DNS resolution failed"),
                            org.springframework.http.HttpMethod.GET,
                            URI.create("https://dev.azure.com/myorg"),
                            org.springframework.http.HttpHeaders.EMPTY));
                })
                .build();
        AzureDevOpsClient client = clientFor(webClient);

        assertThatThrownBy(() -> client.getWorkItemTypeFields(
                "https://dev.azure.com/myorg", "proj-1", "Bug", "Basic dGVzdA=="))
                .isInstanceOf(WebClientRequestException.class);
        assertThat(attempt.get()).isEqualTo(1);
    }

    /**
     * Wire-level check of the WIQL call: stands up a real loopback HTTP server and asserts
     * the exact request line and JSON body Azure DevOps would receive. The ExchangeFunction
     * stubs used above cannot see the serialized body, and the body is precisely what caused
     * ADO to reject every work-item sync with HTTP 400 — the incremental bound was
     * interpolated straight from an Instant and carried sub-second digits, which ADO does
     * not accept in a WIQL DateTime literal.
     */
    @Test
    void queryWorkItemIdsPage_shouldSendDateOnlyBoundAndTopOverTheWire() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = "{\"workItems\":[{\"id\":7}]}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            String orgUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            AzureDevOpsClient client = clientFor(WebClient.builder().build());

            List<Integer> ids = client.queryWorkItemIdsPage(
                    orgUrl, "proj-1", "Basic dGVzdA==",
                    java.time.Instant.parse("2026-08-12T12:34:56.789123Z"), 100, null);

            assertThat(ids).containsExactly(7);
            assertThat(requestUri.get())
                    .startsWith("/proj-1/_apis/wit/wiql?api-version=7.1")
                    .contains("$top=" + AzureDevOpsClient.WIQL_PAGE_SIZE);
            assertThat(requestBody.get())
                    .contains("[System.ChangedDate] >= '2026-08-12'")
                    .contains("[System.Id] > 100")
                    .doesNotContain("789123");
        } finally {
            server.stop(0);
        }
    }

    private WebClient webClientRouting(java.util.function.Function<URI, String> bodyFor, List<URI> seen) {
        return WebClient.builder()
                .exchangeFunction(request -> {
                    seen.add(request.url());
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(bodyFor.apply(request.url()))
                            .build());
                })
                .build();
    }

    private static String entitlement(String id, String kind) {
        return "{\"id\":\"" + id + "\",\"user\":{\"subjectKind\":\"" + kind + "\",\"displayName\":\"N" + id
                + "\",\"mailAddress\":\"" + id + "@x.com\",\"principalName\":\"" + id
                + "@x.com\",\"originId\":\"o" + id + "\"}}";
    }

    @Test
    void listUserEntitlements_followsContinuationTokenAcrossTwoPages_andDropsGroups() {
        List<URI> seen = new java.util.ArrayList<>();
        AzureDevOpsClient client = clientFor(webClientRouting(uri -> uri.toString().contains("continuationToken=tok1")
                ? "{\"items\":[" + entitlement("c", "user") + "]}"
                : "{\"items\":[" + entitlement("a", "user") + "," + entitlement("g", "group")
                        + "],\"continuationToken\":\"tok1\"}", seen));

        var users = client.listUserEntitlements("https://dev.azure.com/myorg/", "Basic x");

        assertThat(users).extracting(AdoUserEntitlementDto::id).containsExactly("a", "c");
        assertThat(users.get(0).originId()).isEqualTo("oa");
        assertThat(users.get(0).mailAddress()).isEqualTo("a@x.com");
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0).toString())
                .isEqualTo("https://vsaex.dev.azure.com/myorg/_apis/userentitlements?api-version=7.1-preview.3");
    }

    @Test
    void listUserEntitlements_acceptsMembersArrayAndVisualStudioUrl() {
        List<URI> seen = new java.util.ArrayList<>();
        AzureDevOpsClient client = clientFor(webClientRouting(
                uri -> "{\"members\":[" + entitlement("a", "user") + "]}", seen));

        assertThat(client.listUserEntitlements("https://myorg.visualstudio.com", "Basic x")).hasSize(1);
        assertThat(seen.get(0).getHost()).isEqualTo("vsaex.dev.azure.com");
        assertThat(seen.get(0).getPath()).startsWith("/myorg/");
    }

    @Test
    void listUserEntitlements_stopsAtTwentyPages() {
        List<URI> seen = new java.util.ArrayList<>();
        AzureDevOpsClient client = clientFor(webClientRouting(
                uri -> "{\"items\":[" + entitlement("a", "user") + "],\"continuationToken\":\"more\"}", seen));

        assertThat(client.listUserEntitlements("https://dev.azure.com/myorg", "Basic x")).hasSize(20);
        assertThat(seen).hasSize(20);
    }

    @Test
    void listUserEntitlements_403And401MapToForbiddenException() {
        for (int status : new int[]{401, 403}) {
            AzureDevOpsClient client = clientFor(webClientReturning(status, "{}"));
            assertThatThrownBy(() -> client.listUserEntitlements("https://dev.azure.com/myorg", "Basic x"))
                    .isInstanceOf(andromeda.sei.ms.feature.azure.domain.exception.AzureEntitlementsForbiddenException.class);
        }
    }

    private static String member(int n, boolean container) {
        return "{\"identity\":{\"id\":\"m" + n + "\",\"displayName\":\"M" + n + "\",\"uniqueName\":\"m" + n
                + "@x.com\",\"isContainer\":" + container + "}}";
    }

    @Test
    void getTeamMembers_pagesWithTopAndSkip_150MembersInTwoPages() {
        List<URI> seen = new java.util.ArrayList<>();
        AzureDevOpsClient client = clientFor(webClientRouting(uri -> {
            boolean second = uri.toString().contains("$skip=100");
            int from = second ? 100 : 0;
            int to = second ? 150 : 100;
            return "{\"value\":[" + java.util.stream.IntStream.range(from, to)
                    .mapToObj(n -> member(n, false)).collect(java.util.stream.Collectors.joining(",")) + "]}";
        }, seen));

        var members = client.getTeamMembers("https://dev.azure.com/myorg", "p", "t", "Basic x");

        assertThat(members).hasSize(150);
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0).toString()).contains("$top=100").contains("$skip=0");
        assertThat(seen.get(1).toString()).contains("$skip=100");
    }

    @Test
    void getTeamMembers_dropsContainers() {
        List<URI> seen = new java.util.ArrayList<>();
        AzureDevOpsClient client = clientFor(webClientRouting(
                uri -> "{\"value\":[" + member(1, false) + "," + member(2, true) + "]}", seen));

        assertThat(client.getTeamMembers("https://dev.azure.com/myorg", "p", "t", "Basic x"))
                .extracting(AdoTeamMemberDto::id).containsExactly("m1");
    }
}

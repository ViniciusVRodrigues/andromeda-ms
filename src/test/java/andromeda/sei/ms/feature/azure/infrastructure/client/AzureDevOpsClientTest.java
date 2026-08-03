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
}

package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.AvailableGithubRepoOutput;
import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.application.usecase.ConfigureGithubConnectionUseCase;
import andromeda.sei.ms.feature.github.application.usecase.DisconnectGithubUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetGithubConnectionStatusUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ListAvailableGithubReposUseCase;
import andromeda.sei.ms.feature.github.application.usecase.TriggerGithubSyncUseCase;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.domain.exception.GithubPatInvalidException;
import andromeda.sei.ms.feature.github.infrastructure.GithubSyncCooldownService;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubConnectionControllerTest {

    @Mock
    private ConfigureGithubConnectionUseCase configureGithubConnectionUseCase;

    @Mock
    private GetGithubConnectionStatusUseCase getGithubConnectionStatusUseCase;

    @Mock
    private DisconnectGithubUseCase disconnectGithubUseCase;

    @Mock
    private TriggerGithubSyncUseCase triggerGithubSyncUseCase;

    @Mock
    private GithubSyncCooldownService githubSyncCooldownService;

    @Mock
    private ListAvailableGithubReposUseCase listAvailableGithubReposUseCase;

    @InjectMocks
    private GithubConnectionController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void configure_shouldReturn204_whenPatValid() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/github")
                        .contentType("application/json")
                        .content("""
                                {"personalAccessToken":"ghp_validtoken"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void configure_shouldReturn422_whenPatInvalid() throws Exception {
        doThrow(new GithubPatInvalidException("GitHub rejected the provided personal access token"))
                .when(configureGithubConnectionUseCase).execute(any());

        mockMvc.perform(post("/api/v1/integrations/github")
                        .contentType("application/json")
                        .content("""
                                {"personalAccessToken":"ghp_badtoken"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GITHUB_PAT_INVALID"));
    }

    @Test
    void configure_shouldReturn400_whenPatBlank() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/github")
                        .contentType("application/json")
                        .content("""
                                {"personalAccessToken":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatus_shouldReturn200_whenConnectionExists() throws Exception {
        var output = new GithubConnectionStatusOutput(
                GithubConnectionStatus.CONNECTED, "user-1", Instant.now(), 0, List.of());
        when(getGithubConnectionStatusUseCase.execute()).thenReturn(output);

        mockMvc.perform(get("/api/v1/integrations/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    @Test
    void getStatus_shouldReturn404_whenNoConnection() throws Exception {
        when(getGithubConnectionStatusUseCase.execute())
                .thenThrow(new GithubConnectionNotFoundException("org-1"));

        mockMvc.perform(get("/api/v1/integrations/github/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GITHUB_CONNECTION_NOT_FOUND"));
    }

    // ── Disconnect (design.md D6) ────────────────────────────────────────

    @Test
    void disconnect_shouldReturn204OnSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/integrations/github"))
                .andExpect(status().isNoContent());
    }

    @Test
    void disconnect_shouldReturn404WhenNoConnection() throws Exception {
        doThrow(new GithubConnectionNotFoundException("org-1"))
                .when(disconnectGithubUseCase).execute();

        mockMvc.perform(delete("/api/v1/integrations/github"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GITHUB_CONNECTION_NOT_FOUND"));
    }

    // ── Manual sync trigger (design.md D7) ───────────────────────────────

    @Test
    void triggerSync_shouldReturn409WhenNoConnection() throws Exception {
        when(githubSyncCooldownService.remainingCooldown(any())).thenReturn(0L);
        doThrow(new GithubConnectionNotFoundException("org-1"))
                .when(triggerGithubSyncUseCase).execute();

        mockMvc.perform(post("/api/v1/integrations/github/sync"))
                .andExpect(status().isConflict());
    }

    @Test
    void triggerSync_shouldReturn429WithRetryAfterWhenCooldownActive() throws Exception {
        when(githubSyncCooldownService.remainingCooldown(any())).thenReturn(18L);

        mockMvc.perform(post("/api/v1/integrations/github/sync"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "18"));
    }

    @Test
    void triggerSync_shouldReturn202AndRecordCooldownOnSuccess() throws Exception {
        when(githubSyncCooldownService.remainingCooldown(any())).thenReturn(0L);

        mockMvc.perform(post("/api/v1/integrations/github/sync"))
                .andExpect(status().isAccepted());

        verify(githubSyncCooldownService).record(any());
    }

    // ── Available repos listing (design.md D5) ───────────────────────────

    @Test
    void listAvailableRepos_shouldReturn200WithRepos() throws Exception {
        when(listAvailableGithubReposUseCase.execute(null))
                .thenReturn(List.of(new AvailableGithubRepoOutput(
                        "octo/hello", "hello", "octo/hello",
                        AvailableGithubRepoOutput.Source.LIVE, null)));

        mockMvc.perform(get("/api/v1/integrations/github/repos/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repoId").value("octo/hello"))
                .andExpect(jsonPath("$[0].fullName").value("octo/hello"))
                .andExpect(jsonPath("$[0].source").value("LIVE"));
    }

    @Test
    void listAvailableRepos_shouldPassForProjectIdToUseCase() throws Exception {
        when(listAvailableGithubReposUseCase.execute(eq("proj-1")))
                .thenReturn(List.of(new AvailableGithubRepoOutput(
                        "octo/hello", "hello", "octo/hello",
                        AvailableGithubRepoOutput.Source.CACHED, "proj-1")));

        mockMvc.perform(get("/api/v1/integrations/github/repos/available")
                        .param("forProjectId", "proj-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].boundToProjectId").value("proj-1"));
    }

    @Test
    void listAvailableRepos_shouldReturn404WhenNoConnection() throws Exception {
        when(listAvailableGithubReposUseCase.execute(any()))
                .thenThrow(new GithubConnectionNotFoundException("org-1"));

        mockMvc.perform(get("/api/v1/integrations/github/repos/available"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GITHUB_CONNECTION_NOT_FOUND"));
    }
}

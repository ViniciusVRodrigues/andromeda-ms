package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.application.usecase.ConfigureGithubConnectionUseCase;
import andromeda.sei.ms.feature.github.application.usecase.DisconnectGithubUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetGithubConnectionStatusUseCase;
import andromeda.sei.ms.feature.github.application.usecase.TriggerGithubSyncUseCase;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.domain.exception.GithubPatInvalidException;
import andromeda.sei.ms.feature.sync.domain.exception.SyncCooldownActiveException;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.ListBindableRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
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
    private ListBindableRepositoriesUseCase listBindableRepositoriesUseCase;

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
        doThrow(new GithubConnectionNotFoundException("org-1"))
                .when(triggerGithubSyncUseCase).execute();

        mockMvc.perform(post("/api/v1/integrations/github/sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GITHUB_CONNECTION_NOT_FOUND"));
    }

    @Test
    void triggerSync_shouldReturn429WithRetryAfterWhenCooldownActive() throws Exception {
        when(triggerGithubSyncUseCase.execute()).thenThrow(new SyncCooldownActiveException(18));

        mockMvc.perform(post("/api/v1/integrations/github/sync"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "18"));
    }

    @Test
    void triggerSync_shouldReturn202WithSyncIdBeforeSyncFinishes() throws Exception {
        when(triggerGithubSyncUseCase.execute()).thenReturn("sync-1");

        mockMvc.perform(post("/api/v1/integrations/github/sync"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncId").value("sync-1"));
    }

    // ── Deprecated available repos listing, now over the neutral use case ──

    @Test
    void listAvailableRepos_shouldKeepLegacyShape_andDropReposBoundElsewhere() throws Exception {
        when(listBindableRepositoriesUseCase.execute(VcsProvider.GITHUB)).thenReturn(List.of(
                bindable("octo/hello", true, null, BindableRepositoryOutput.Source.LIVE),
                bindable("octo/taken", false, "proj-9", BindableRepositoryOutput.Source.LIVE),
                bindable("octo/mine", false, "proj-1", BindableRepositoryOutput.Source.CACHED)));

        mockMvc.perform(get("/api/v1/integrations/github/repos/available").param("forProjectId", "proj-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].repoId").value("octo/hello"))
                .andExpect(jsonPath("$[0].name").value("hello"))
                .andExpect(jsonPath("$[0].fullName").value("octo/hello"))
                .andExpect(jsonPath("$[0].source").value("LIVE"))
                .andExpect(jsonPath("$[1].boundToProjectId").value("proj-1"));
    }

    @Test
    void listAvailableRepos_shouldReturn409WhenNoConnection() throws Exception {
        when(listBindableRepositoriesUseCase.execute(any())).thenThrow(new VcsConnectionNotFoundException("GITHUB"));

        mockMvc.perform(get("/api/v1/integrations/github/repos/available"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VCS_CONNECTION_NOT_FOUND"));
    }

    private static BindableRepositoryOutput bindable(String name, boolean bindable, String boundTo,
                                                     BindableRepositoryOutput.Source source) {
        return new BindableRepositoryOutput(VcsProvider.GITHUB, "1", name, null, null, "main", bindable, boundTo, source);
    }
}

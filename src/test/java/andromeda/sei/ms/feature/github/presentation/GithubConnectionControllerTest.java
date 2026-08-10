package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.application.usecase.ConfigureGithubConnectionUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetGithubConnectionStatusUseCase;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.domain.exception.GithubPatInvalidException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubConnectionControllerTest {

    @Mock
    private ConfigureGithubConnectionUseCase configureGithubConnectionUseCase;

    @Mock
    private GetGithubConnectionStatusUseCase getGithubConnectionStatusUseCase;

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
}

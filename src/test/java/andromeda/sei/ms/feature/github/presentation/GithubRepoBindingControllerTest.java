package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.usecase.BindGithubRepoUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ToggleGithubRepoSyncUseCase;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoBindingNotFoundException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubRepoBindingControllerTest {

    @Mock private BindGithubRepoUseCase bindGithubRepoUseCase;
    @Mock private ToggleGithubRepoSyncUseCase toggleGithubRepoSyncUseCase;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;

    @InjectMocks
    private GithubRepoBindingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bindRepo_shouldReturn200() throws Exception {
        var binding = binding("binding-1", "proj-1", "octo/repo", true);
        when(bindGithubRepoUseCase.execute(eq("proj-1"), any())).thenReturn(binding);

        mockMvc.perform(post("/api/v1/integrations/github/projects/proj-1/bindings")
                        .contentType("application/json")
                        .content("""
                                {"repoId":"octo/repo","syncEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repoId").value("octo/repo"));
    }

    @Test
    void toggleSync_shouldReturn404_whenBindingNotFound() throws Exception {
        when(toggleGithubRepoSyncUseCase.execute(eq("missing"), any()))
                .thenThrow(new GithubRepoBindingNotFoundException("missing"));

        mockMvc.perform(patch("/api/v1/integrations/github/bindings/missing")
                        .contentType("application/json")
                        .content("""
                                {"syncEnabled":false}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GITHUB_REPO_BINDING_NOT_FOUND"));
    }

    @Test
    void listBindings_shouldReturn200WithBindings() throws Exception {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndActiveTrue(any()))
                .thenReturn(List.of(binding("binding-1", "proj-1", "octo/repo", true)));

        mockMvc.perform(get("/api/v1/integrations/github/bindings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("binding-1"));
    }

    private GithubRepoBinding binding(String id, String projectId, String repoId, boolean syncEnabled) {
        Instant now = Instant.now();
        return new GithubRepoBinding(id, "org-1", projectId, repoId, syncEnabled, null, true, now, now, null);
    }
}

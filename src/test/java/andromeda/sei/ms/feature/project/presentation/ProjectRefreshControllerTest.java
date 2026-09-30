package andromeda.sei.ms.feature.project.presentation;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.project.application.dto.ProjectRefreshStatusOutput;
import andromeda.sei.ms.feature.project.application.usecase.GetProjectRefreshStatusUseCase;
import andromeda.sei.ms.feature.project.application.usecase.RefreshProjectUseCase;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNoIntegrationBindingException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectRefreshNotFoundException;
import andromeda.sei.ms.feature.sync.domain.exception.SyncCooldownActiveException;
import andromeda.sei.ms.feature.sync.domain.exception.SyncQueueFullException;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc + Mockito (padrao dos controllers do codebase); {@code @PreAuthorize}
 * ({@code AZURE_READ} ou {@code REPOS_READ}) nao e avaliado neste modo.
 */
@ExtendWith(MockitoExtension.class)
class ProjectRefreshControllerTest {

    @Mock private RefreshProjectUseCase refreshProjectUseCase;
    @Mock private GetProjectRefreshStatusUseCase getProjectRefreshStatusUseCase;

    @InjectMocks
    private ProjectRefreshController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn202WithSyncId() throws Exception {
        when(refreshProjectUseCase.execute("proj-1")).thenReturn("sync-1");

        mockMvc.perform(post("/api/v1/projects/proj-1/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncId").value("sync-1"));
    }

    @Test
    void shouldReturn404ForProjectOutsideTheOrganization() throws Exception {
        when(refreshProjectUseCase.execute("proj-x")).thenThrow(new ProjectNotFoundException("proj-x"));

        mockMvc.perform(post("/api/v1/projects/proj-x/refresh"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void shouldReturn409WhenProjectHasNoBinding() throws Exception {
        when(refreshProjectUseCase.execute("proj-1")).thenThrow(new ProjectNoIntegrationBindingException("proj-1"));

        mockMvc.perform(post("/api/v1/projects/proj-1/refresh"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_NO_INTEGRATION_BINDING"));
    }

    @Test
    void shouldReturn429WithRetryAfterWhenEveryBindingIsInCooldown() throws Exception {
        when(refreshProjectUseCase.execute("proj-1")).thenThrow(new SyncCooldownActiveException(25));

        mockMvc.perform(post("/api/v1/projects/proj-1/refresh"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "25"))
                .andExpect(jsonPath("$.code").value("SYNC_COOLDOWN_ACTIVE"));
    }

    @Test
    void shouldReturn429WhenSyncQueueIsFull() throws Exception {
        when(refreshProjectUseCase.execute("proj-1")).thenThrow(new SyncQueueFullException());

        mockMvc.perform(post("/api/v1/projects/proj-1/refresh"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", String.valueOf(SyncQueueFullException.RETRY_AFTER_SECONDS)))
                .andExpect(jsonPath("$.code").value("SYNC_QUEUE_FULL"));
    }

    @Test
    void shouldReturnRefreshStatusWithBindings() throws Exception {
        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        var output = new ProjectRefreshStatusOutput("sync-1", "proj-1", SyncStatus.PARTIAL, SyncTrigger.MANUAL,
                now, now.plusSeconds(20), List.of(
                new ProjectRefreshStatusOutput.BindingStatus("az-1", Integration.AZURE_DEVOPS, SyncStatus.SUCCESS,
                        null, null, now, now.plusSeconds(10), 40, 0),
                new ProjectRefreshStatusOutput.BindingStatus("gh-1", Integration.GITHUB, SyncStatus.FAILED,
                        "GITHUB_SYNC_FAILED", "boom", now, now.plusSeconds(20), 0, 0)));
        when(getProjectRefreshStatusUseCase.execute("proj-1", "sync-1")).thenReturn(output);

        mockMvc.perform(get("/api/v1/projects/proj-1/refresh/sync-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncId").value("sync-1"))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.bindings.length()").value(2))
                .andExpect(jsonPath("$.bindings[1].integration").value("GITHUB"))
                .andExpect(jsonPath("$.bindings[1].status").value("FAILED"))
                .andExpect(jsonPath("$.bindings[1].errorCode").value("GITHUB_SYNC_FAILED"));
    }

    @Test
    void shouldReturn404ForUnknownRefresh() throws Exception {
        when(getProjectRefreshStatusUseCase.execute("proj-1", "nope")).thenThrow(new ProjectRefreshNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/projects/proj-1/refresh/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_REFRESH_NOT_FOUND"));
    }
}

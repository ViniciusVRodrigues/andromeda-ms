package andromeda.sei.ms.feature.integration.presentation;

import andromeda.sei.ms.feature.integration.application.dto.SyncLogOutput;
import andromeda.sei.ms.feature.integration.application.usecase.GetIntegrationSyncLogsUseCase;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IntegrationSyncLogControllerTest {

    @Mock
    private GetIntegrationSyncLogsUseCase getIntegrationSyncLogsUseCase;

    @Mock
    private CurrentUserPermissions currentUserPermissions;

    @InjectMocks
    private IntegrationSyncLogController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn200WithLogsForAzureDevOps() throws Exception {
        var log = new SyncLogOutput("log-1", Integration.AZURE_DEVOPS, SyncOperation.SYNC_SPRINTS,
                SyncStatus.SUCCESS, SyncTrigger.SCHEDULED, null,
                Instant.now(), Instant.now(), 100L, 5, 0, null, null, null, null, 2, java.util.List.of("Issue", "Risk"));
        when(getIntegrationSyncLogsUseCase.execute(eq(Integration.AZURE_DEVOPS), any(), any()))
                .thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/integrations/sync-logs?integration=AZURE_DEVOPS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log-1"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void shouldReturn5xxWhenIntegrationParamIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/sync-logs"))
                .andExpect(status().is5xxServerError());
    }
    @Test
    void shouldExposeUnmappedTypeFields() throws Exception {
        var log = new SyncLogOutput("log-2", Integration.AZURE_DEVOPS, SyncOperation.SYNC_WORK_ITEMS,
                SyncStatus.SUCCESS, SyncTrigger.SCHEDULED, "proj-1",
                Instant.now(), Instant.now(), 100L, 5, 0, null, null, null, null,
                5, java.util.List.of("Issue", "Risk"));
        when(getIntegrationSyncLogsUseCase.execute(eq(Integration.AZURE_DEVOPS), any(), any()))
                .thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/integrations/sync-logs?integration=AZURE_DEVOPS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemsUnmappedType").value(5))
                .andExpect(jsonPath("$[0].unmappedTypes[0]").value("Issue"))
                .andExpect(jsonPath("$[0].unmappedTypes[1]").value("Risk"));
    }
}

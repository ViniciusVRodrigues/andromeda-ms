package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.application.usecase.BindAzureProjectUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureProjectsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.RemoveAzureProjectBindingUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureProjectBindingSyncUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateWorkflowConfigUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UpdateWorkflowConfigControllerTest {

    @Mock private ListAzureProjectsUseCase listAzureProjectsUseCase;
    @Mock private BindAzureProjectUseCase bindAzureProjectUseCase;
    @Mock private RemoveAzureProjectBindingUseCase removeAzureProjectBindingUseCase;
    @Mock private UpdateAzureProjectBindingSyncUseCase updateAzureProjectBindingSyncUseCase;
    @Mock private UpdateWorkflowConfigUseCase updateWorkflowConfigUseCase;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;

    @InjectMocks
    private AzureProjectBindingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateWorkflowConfig_shouldReturn200WithUpdatedConfig() throws Exception {
        Instant now = Instant.now();
        AzureProjectBinding binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-id-1", "My Project",
                true, null,
                new WorkflowConfig(List.of(), List.of(), WorkflowConfig.ConfigSource.CUSTOM),
                true, now, now, null);
        when(updateWorkflowConfigUseCase.execute(eq("bind-1"), any(UpdateWorkflowConfigInput.class)))
                .thenReturn(binding);

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statusMappings": [{"rawStatus": "Active", "resolvedStatus": "IN_PROGRESS"}],
                                  "typeMappings": [{"rawType": "Bug", "resolvedType": "BUG"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowConfig.configSource").value("CUSTOM"));
    }

    @Test
    void updateWorkflowConfig_shouldReturn404WhenBindingNotFound() throws Exception {
        when(updateWorkflowConfigUseCase.execute(eq("bad-id"), any()))
                .thenThrow(new AzureBindingNotFoundException("bad-id"));

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bad-id/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statusMappings": [], "typeMappings": []}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWorkflowConfig_shouldReturn400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statusMappings": null, "typeMappings": null}
                                """))
                .andExpect(status().isBadRequest());
    }
}

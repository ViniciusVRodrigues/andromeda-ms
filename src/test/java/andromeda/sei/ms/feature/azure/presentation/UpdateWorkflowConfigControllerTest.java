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
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureFieldMappingDuplicateTargetException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureHierarchyLevelDuplicateTypeException;
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
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ForceResyncProjectUseCase forceResyncProjectUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypesUseCase listAzureWorkItemTypesUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypeFieldsUseCase listAzureWorkItemTypeFieldsUseCase;

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
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.CUSTOM),
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

    @Test
    void updateWorkflowConfig_shouldReturn422WhenFieldMappingHasDuplicateTarget() throws Exception {
        when(updateWorkflowConfigUseCase.execute(eq("bind-1"), any(UpdateWorkflowConfigInput.class)))
                .thenThrow(new AzureFieldMappingDuplicateTargetException("EFFORT"));

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statusMappings": [],
                                  "typeMappings": [],
                                  "fieldMappings": [
                                    {"internalField": "EFFORT", "azureFieldReferenceName": "Microsoft.VSTS.Scheduling.Effort", "azureFieldType": "double"},
                                    {"internalField": "EFFORT", "azureFieldReferenceName": "Microsoft.VSTS.Scheduling.StoryPoints", "azureFieldType": "double"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateWorkflowConfig_shouldReturnFieldMappingsAndSyncFilterInResponse() throws Exception {
        Instant now = Instant.now();
        var fieldMapping = new andromeda.sei.ms.feature.azure.domain.FieldMapping(
                andromeda.sei.ms.feature.azure.domain.FieldMapping.InternalFieldTarget.EFFORT,
                "Microsoft.VSTS.Scheduling.Effort", "double");
        var syncFilter = new andromeda.sei.ms.feature.azure.domain.WiqlFilter(
                List.of("Bug"), "MyProject\\TeamA", null);
        AzureProjectBinding binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-id-1", "My Project",
                true, null,
                new WorkflowConfig(List.of(), List.of(), List.of(fieldMapping), syncFilter, WorkflowConfig.ConfigSource.CUSTOM),
                true, now, now, null);
        when(updateWorkflowConfigUseCase.execute(eq("bind-1"), any(UpdateWorkflowConfigInput.class)))
                .thenReturn(binding);

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statusMappings": [],
                                  "typeMappings": [],
                                  "fieldMappings": [
                                    {"internalField": "EFFORT", "azureFieldReferenceName": "Microsoft.VSTS.Scheduling.Effort", "azureFieldType": "double"}
                                  ],
                                  "syncFilter": {"workItemTypes": ["Bug"], "areaPath": "MyProject\\\\TeamA", "iterationPath": null}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowConfig.fieldMappings[0].internalField").value("EFFORT"))
                .andExpect(jsonPath("$.workflowConfig.fieldMappings[0].azureFieldReferenceName").value("Microsoft.VSTS.Scheduling.Effort"))
                .andExpect(jsonPath("$.workflowConfig.syncFilter.workItemTypes[0]").value("Bug"))
                .andExpect(jsonPath("$.workflowConfig.syncFilter.areaPath").value("MyProject\\TeamA"));
    }

    @Test
    void updateWorkflowConfig_shouldSaveAndReturnHierarchyLevels() throws Exception {
        Instant now = Instant.now();
        AzureProjectBinding binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-id-1", "My Project",
                true, null,
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.CUSTOM,
                        List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1))),
                true, now, now, null);
        when(updateWorkflowConfigUseCase.execute(eq("bind-1"), any(UpdateWorkflowConfigInput.class)))
                .thenReturn(binding);

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statusMappings": [],
                                  "typeMappings": [],
                                  "hierarchyLevels": [
                                    {"rawType": "Epic", "rank": 0},
                                    {"rawType": "Task", "rank": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowConfig.hierarchyLevels[0].rawType").value("Epic"))
                .andExpect(jsonPath("$.workflowConfig.hierarchyLevels[0].rank").value(0))
                .andExpect(jsonPath("$.workflowConfig.hierarchyLevels[1].rawType").value("Task"));
    }

    @Test
    void updateWorkflowConfig_shouldReturn422WhenHierarchyLevelHasDuplicateRawType() throws Exception {
        when(updateWorkflowConfigUseCase.execute(eq("bind-1"), any(UpdateWorkflowConfigInput.class)))
                .thenThrow(new AzureHierarchyLevelDuplicateTypeException("Task"));

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statusMappings": [],
                                  "typeMappings": [],
                                  "hierarchyLevels": [
                                    {"rawType": "Task", "rank": 0},
                                    {"rawType": "Task", "rank": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateWorkflowConfig_shouldAcceptEmptyHierarchyLevels() throws Exception {
        Instant now = Instant.now();
        AzureProjectBinding binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-id-1", "My Project",
                true, null,
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.CUSTOM, List.of()),
                true, now, now, null);
        when(updateWorkflowConfigUseCase.execute(eq("bind-1"), any(UpdateWorkflowConfigInput.class)))
                .thenReturn(binding);

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statusMappings": [], "typeMappings": [], "hierarchyLevels": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowConfig.hierarchyLevels").isEmpty());
    }
}

package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.AzureProjectOutput;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemTypeFieldOutput;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemTypeOutput;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemTypeStateOutput;
import andromeda.sei.ms.feature.azure.application.usecase.BindAzureProjectUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureProjectsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.RemoveAzureProjectBindingUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureProjectBindingSyncUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureIntrospectionFailedException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureWorkItemTypeNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureWorkflowConfigMappingsEmptyException;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AzureProjectBindingControllerTest {

    @Mock private ListAzureProjectsUseCase listAzureProjectsUseCase;
    @Mock private BindAzureProjectUseCase bindAzureProjectUseCase;
    @Mock private RemoveAzureProjectBindingUseCase removeAzureProjectBindingUseCase;
    @Mock private UpdateAzureProjectBindingSyncUseCase updateAzureProjectBindingSyncUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.UpdateWorkflowConfigUseCase updateWorkflowConfigUseCase;
    @Mock private andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ForceResyncProjectUseCase forceResyncProjectUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypesUseCase listAzureWorkItemTypesUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypeFieldsUseCase listAzureWorkItemTypeFieldsUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ListAzureWorkItemTypeStatesUseCase listAzureWorkItemTypeStatesUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.ListSiblingAzureProjectBindingsUseCase listSiblingAzureProjectBindingsUseCase;
    @Mock private andromeda.sei.ms.feature.azure.application.usecase.RepairAzureDataUseCase repairAzureDataUseCase;
    @Mock private andromeda.sei.ms.feature.integration.application.usecase.WipeIntegrationDataUseCase wipeIntegrationDataUseCase;

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
    void listProjects_shouldReturn200WithProjects() throws Exception {
        when(listAzureProjectsUseCase.execute(isNull())).thenReturn(List.of(
                new AzureProjectOutput("azure-id-1", "My Project")));

        mockMvc.perform(get("/api/v1/integrations/azure/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].azureProjectId").value("azure-id-1"))
                .andExpect(jsonPath("$[0].azureProjectName").value("My Project"));
    }

    @Test
    void listProjects_shouldPassForProjectIdParamToUseCase() throws Exception {
        when(listAzureProjectsUseCase.execute(eq("proj-1"))).thenReturn(List.of(
                new AzureProjectOutput("azure-id-1", "My Project")));

        mockMvc.perform(get("/api/v1/integrations/azure/projects")
                        .param("forProjectId", "proj-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].azureProjectId").value("azure-id-1"));
    }

    @Test
    void listProjects_shouldReturn404WhenNoConnection() throws Exception {
        when(listAzureProjectsUseCase.execute(any()))
                .thenThrow(new AzureConnectionNotFoundException("org-1"));

        mockMvc.perform(get("/api/v1/integrations/azure/projects"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bindProject_shouldReturn200OnSuccess() throws Exception {
        var binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-id-1", "My Project",
                true, null, null, true, Instant.now(), Instant.now(), null);
        when(bindAzureProjectUseCase.execute(eq("proj-1"), any())).thenReturn(binding);

        mockMvc.perform(post("/api/v1/integrations/azure/projects/proj-1/binding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"azureProjectId":"azure-id-1","azureProjectName":"My Project"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void removeBinding_shouldReturn404WhenBindingNotFound() throws Exception {
        doThrow(new AzureBindingNotFoundException("proj-1"))
                .when(removeAzureProjectBindingUseCase).execute("proj-1");

        mockMvc.perform(delete("/api/v1/integrations/azure/projects/proj-1/binding"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeBinding_shouldReturn204OnSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/integrations/azure/projects/proj-1/binding"))
                .andExpect(status().isNoContent());
    }

    @Test
    void listWorkItemTypes_shouldReturn200WithTypes() throws Exception {
        when(listAzureWorkItemTypesUseCase.execute("bind-1")).thenReturn(List.of(
                new AzureWorkItemTypeOutput("Bug", "Microsoft.VSTS.WorkItemTypes.Bug"),
                new AzureWorkItemTypeOutput("User Story", "Microsoft.VSTS.WorkItemTypes.UserStory")));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/process/work-item-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bug"))
                .andExpect(jsonPath("$[1].name").value("User Story"));
    }

    @Test
    void listWorkItemTypes_shouldReturn404WhenBindingNotFound() throws Exception {
        when(listAzureWorkItemTypesUseCase.execute("bad-id"))
                .thenThrow(new AzureBindingNotFoundException("bad-id"));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bad-id/process/work-item-types"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listWorkItemTypeFields_shouldReturn200WithFields() throws Exception {
        when(listAzureWorkItemTypeFieldsUseCase.execute("bind-1", "Bug")).thenReturn(List.of(
                new AzureWorkItemTypeFieldOutput("Microsoft.VSTS.Scheduling.Effort", "Effort", "double", false),
                new AzureWorkItemTypeFieldOutput("Custom.Complexidade", "Complexidade", "integer", false)));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/process/work-item-types/Bug/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenceName").value("Microsoft.VSTS.Scheduling.Effort"))
                .andExpect(jsonPath("$[1].referenceName").value("Custom.Complexidade"));
    }

    @Test
    void listWorkItemTypeFields_shouldReturn404WhenTypeNotFound() throws Exception {
        when(listAzureWorkItemTypeFieldsUseCase.execute("bind-1", "Unknown"))
                .thenThrow(new AzureWorkItemTypeNotFoundException("Unknown"));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/process/work-item-types/Unknown/fields"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWorkflowConfig_shouldReturn422WhenEmptyMappingsWouldWipeExistingConfig() throws Exception {
        when(updateWorkflowConfigUseCase.execute(org.mockito.ArgumentMatchers.eq("bind-1"), any()))
                .thenThrow(new AzureWorkflowConfigMappingsEmptyException(
                        "Refusing to replace existing non-empty statusMappings with an empty list for binding bind-1"));

        mockMvc.perform(put("/api/v1/integrations/azure/bindings/bind-1/workflow-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statusMappings":[],"typeMappings":[]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AZURE_WORKFLOW_CONFIG_MAPPINGS_EMPTY"));
    }

    @Test
    void listWorkItemTypeStates_shouldReturn200WithStates() throws Exception {
        when(listAzureWorkItemTypeStatesUseCase.execute("bind-1", "Bug")).thenReturn(List.of(
                new AzureWorkItemTypeStateOutput("New", "b2b2b2", "Proposed"),
                new AzureWorkItemTypeStateOutput("Active", "007acc", "InProgress")));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/process/work-item-types/Bug/states"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("New"))
                .andExpect(jsonPath("$[1].name").value("Active"));
    }

    @Test
    void listWorkItemTypeStates_shouldReturn404WhenBindingNotFound() throws Exception {
        when(listAzureWorkItemTypeStatesUseCase.execute("bad-id", "Bug"))
                .thenThrow(new AzureBindingNotFoundException("bad-id"));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bad-id/process/work-item-types/Bug/states"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listWorkItemTypeStates_shouldReturn404WhenTypeNotFound() throws Exception {
        when(listAzureWorkItemTypeStatesUseCase.execute("bind-1", "Unknown"))
                .thenThrow(new AzureWorkItemTypeNotFoundException("Unknown"));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/process/work-item-types/Unknown/states"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listSiblings_shouldReturn200WithSiblingsExcludingCurrentBinding() throws Exception {
        var sibling = new AzureProjectBinding(
                "bind-2", "org-1", "proj-2", "azure-id-2", "Other Project",
                true, null, null, true, Instant.now(), Instant.now(), null);
        when(listSiblingAzureProjectBindingsUseCase.execute("bind-1")).thenReturn(List.of(sibling));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/siblings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bind-2"))
                .andExpect(jsonPath("$[0].azureProjectName").value("Other Project"));
    }

    @Test
    void listSiblings_shouldReturn200WithEmptyListWhenNoSiblingsExist() throws Exception {
        when(listSiblingAzureProjectBindingsUseCase.execute("bind-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/siblings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listWorkItemTypeFields_shouldReturn422WhenIntrospectionFails() throws Exception {
        // NOTE: proposal/spec describe this as HTTP 502, but GlobalExceptionHandler maps
        // BusinessRuleException (the exception's actual base type) to 422 uniformly across
        // the app; no per-exception override exists. Asserting actual behavior here.
        when(listAzureWorkItemTypeFieldsUseCase.execute("bind-1", "Bug"))
                .thenThrow(new AzureIntrospectionFailedException("ADO call failed"));

        mockMvc.perform(get("/api/v1/integrations/azure/bindings/bind-1/process/work-item-types/Bug/fields"))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * O escopo do wipe vem do TenantContextHolder, nunca do cliente: um organizationId enviado
     * na query string tem que ser simplesmente ignorado.
     *
     * <p>Nota: este setup e standaloneSetup, que nao carrega o Spring Security — entao ele NAO
     * exercita o @PreAuthorize. A checagem de permissao (403) e responsabilidade da anotacao no
     * controller e nao esta coberta aqui; o projeto nao tem hoje nenhum teste que suba o filtro
     * de seguranca.
     */
    @Test
    void wipeData_shouldUseTenantFromContextAndIgnoreClientSuppliedOrg() throws Exception {
        andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder.set("org-do-token");
        try {
            mockMvc.perform(delete("/api/v1/integrations/azure/data")
                            .param("organizationId", "org-de-outro-tenant"))
                    .andExpect(status().isAccepted());

            org.mockito.ArgumentCaptor<String> orgCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(wipeIntegrationDataUseCase).execute(
                    orgCaptor.capture(),
                    eq(andromeda.sei.ms.feature.integration.domain.Integration.AZURE_DEVOPS),
                    any());

            org.assertj.core.api.Assertions.assertThat(orgCaptor.getValue()).isEqualTo("org-do-token");
        } finally {
            andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder.clear();
        }
    }

    /** Sem projectId o escopo e a integracao inteira; com projectId, so aquele binding. */
    @Test
    void wipeData_shouldScopeToProjectWhenProvided() throws Exception {
        andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder.set("org-1");
        try {
            mockMvc.perform(delete("/api/v1/integrations/azure/data").param("projectId", "proj-1"))
                    .andExpect(status().isAccepted());

            org.mockito.ArgumentCaptor<andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope> captor =
                    org.mockito.ArgumentCaptor.forClass(
                            andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope.class);
            org.mockito.Mockito.verify(wipeIntegrationDataUseCase).execute(any(), any(), captor.capture());

            org.assertj.core.api.Assertions.assertThat(captor.getValue().projectId()).isEqualTo("proj-1");
            org.assertj.core.api.Assertions.assertThat(captor.getValue().isWholeIntegration()).isFalse();
        } finally {
            andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder.clear();
        }
    }

    @Test
    void repair_shouldReturn202AndDelegateWithTenant() throws Exception {
        andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder.set("org-1");
        try {
            mockMvc.perform(post("/api/v1/integrations/azure/repair"))
                    .andExpect(status().isAccepted());

            org.mockito.Mockito.verify(repairAzureDataUseCase).execute("org-1", null);
        } finally {
            andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder.clear();
        }
    }
}

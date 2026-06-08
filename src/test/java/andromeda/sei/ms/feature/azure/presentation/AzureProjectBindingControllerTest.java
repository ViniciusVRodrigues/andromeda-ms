package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.AzureProjectOutput;
import andromeda.sei.ms.feature.azure.application.usecase.BindAzureProjectUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureProjectsUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.RemoveAzureProjectBindingUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureProjectBindingSyncUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        when(listAzureProjectsUseCase.execute()).thenReturn(List.of(
                new AzureProjectOutput("azure-id-1", "My Project")));

        mockMvc.perform(get("/api/v1/integrations/azure/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].azureProjectId").value("azure-id-1"))
                .andExpect(jsonPath("$[0].azureProjectName").value("My Project"));
    }

    @Test
    void listProjects_shouldReturn404WhenNoConnection() throws Exception {
        when(listAzureProjectsUseCase.execute())
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
}

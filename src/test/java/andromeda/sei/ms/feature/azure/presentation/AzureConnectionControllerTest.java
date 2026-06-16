package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.AzureConnectionStatusOutput;
import andromeda.sei.ms.feature.azure.application.usecase.ConfigureAzureDevOpsPatUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.DisconnectAzureUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.GetAzureConnectionStatusUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.TriggerAzureSyncUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.AzureSyncCooldownService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AzureConnectionControllerTest {

    @Mock
    private GetAzureConnectionStatusUseCase getAzureConnectionStatusUseCase;

    @Mock
    private DisconnectAzureUseCase disconnectAzureUseCase;

    @Mock
    private TriggerAzureSyncUseCase triggerAzureSyncUseCase;

    @Mock
    private AzureSyncCooldownService azureSyncCooldownService;

    @Mock
    private ConfigureAzureDevOpsPatUseCase configureAzureDevOpsPatUseCase;

    @InjectMocks
    private AzureConnectionController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getStatus_shouldReturn200WhenConnectionExists() throws Exception {
        var output = new AzureConnectionStatusOutput(
                AzureConnectionStatus.CONNECTED, "https://dev.azure.com/myorg",
                "user-1", Instant.now(), 0, AzureAuthMethod.OAUTH);
        when(getAzureConnectionStatusUseCase.execute()).thenReturn(output);

        mockMvc.perform(get("/api/v1/integrations/azure/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.azureOrgUrl").value("https://dev.azure.com/myorg"));
    }

    @Test
    void getStatus_shouldReturn404WhenNoConnection() throws Exception {
        when(getAzureConnectionStatusUseCase.execute())
                .thenThrow(new AzureConnectionNotFoundException("org-1"));

        mockMvc.perform(get("/api/v1/integrations/azure/connection"))
                .andExpect(status().isNotFound());
    }

    @Test
    void disconnect_shouldReturn204OnSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/integrations/azure/connection"))
                .andExpect(status().isNoContent());
    }

    @Test
    void disconnect_shouldReturn404WhenNoConnection() throws Exception {
        doThrow(new AzureConnectionNotFoundException("org-1"))
                .when(disconnectAzureUseCase).execute();

        mockMvc.perform(delete("/api/v1/integrations/azure/connection"))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggerSync_shouldReturn409WhenNoConnection() throws Exception {
        when(azureSyncCooldownService.remainingCooldown(any())).thenReturn(0L);
        doThrow(new AzureConnectionNotFoundException("org-1"))
                .when(triggerAzureSyncUseCase).execute();

        mockMvc.perform(post("/api/v1/integrations/azure/sync"))
                .andExpect(status().isConflict());
    }

    @Test
    void triggerSync_shouldReturn429WithRetryAfterWhenCooldownActive() throws Exception {
        when(azureSyncCooldownService.remainingCooldown(any())).thenReturn(18L);

        mockMvc.perform(post("/api/v1/integrations/azure/sync"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "18"));
    }

    @Test
    void triggerSync_shouldReturn202OnSuccess() throws Exception {
        when(azureSyncCooldownService.remainingCooldown(any())).thenReturn(0L);

        mockMvc.perform(post("/api/v1/integrations/azure/sync"))
                .andExpect(status().isAccepted());
    }

    @Test
    void connectPat_shouldReturn200OnValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/azure/connection/pat")
                        .contentType("application/json")
                        .content("{\"adoOrganizationUrl\":\"https://dev.azure.com/myorg\",\"personalAccessToken\":\"myPat\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void connectPat_shouldReturn400WhenFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/azure/connection/pat")
                        .contentType("application/json")
                        .content("{\"adoOrganizationUrl\":\"\",\"personalAccessToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}

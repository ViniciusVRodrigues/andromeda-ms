package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.AzureConnectionStatusOutput;
import andromeda.sei.ms.feature.azure.application.usecase.ConfigureAzureDevOpsPatUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.DisconnectAzureUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.GetAzureConnectionStatusUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureOrgUrlUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.TriggerAzureSyncUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import andromeda.sei.ms.feature.sync.domain.exception.SyncCooldownActiveException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private ConfigureAzureDevOpsPatUseCase configureAzureDevOpsPatUseCase;

    @Mock
    private UpdateAzureOrgUrlUseCase updateAzureOrgUrlUseCase;

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
        doThrow(new AzureConnectionNotFoundException("org-1"))
                .when(triggerAzureSyncUseCase).execute();

        mockMvc.perform(post("/api/v1/integrations/azure/sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AZURE_CONNECTION_NOT_FOUND"));
    }

    @Test
    void triggerSync_shouldReturn429WithRetryAfterWhenCooldownActive() throws Exception {
        when(triggerAzureSyncUseCase.execute()).thenThrow(new SyncCooldownActiveException(18));

        mockMvc.perform(post("/api/v1/integrations/azure/sync"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "18"));
    }

    @Test
    void triggerSync_shouldReturn202WithSyncIdBeforeSyncFinishes() throws Exception {
        when(triggerAzureSyncUseCase.execute()).thenReturn("sync-1");

        mockMvc.perform(post("/api/v1/integrations/azure/sync"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncId").value("sync-1"));
    }

    @Test
    void connectPat_shouldReturn204OnValidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/azure/connection/pat")
                        .contentType("application/json")
                        .content("{\"adoOrganizationUrl\":\"https://dev.azure.com/myorg\",\"personalAccessToken\":\"myPat\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void connectPat_shouldReturn400WhenFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/azure/connection/pat")
                        .contentType("application/json")
                        .content("{\"adoOrganizationUrl\":\"\",\"personalAccessToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void connectPat_shouldReturn422WithAzureOrgUrlInvalidCodeWhenUrlMalformed() throws Exception {
        doThrow(new AzureOrgUrlInvalidException(
                "adoOrganizationUrl must match https://dev.azure.com/{org} or https://{org}.visualstudio.com"))
                .when(configureAzureDevOpsPatUseCase).execute(any());

        mockMvc.perform(post("/api/v1/integrations/azure/connection/pat")
                        .contentType("application/json")
                        .content("{\"adoOrganizationUrl\":\"ghp_aFakePersonalAccessTokenValue1234567890\",\"personalAccessToken\":\"myPat\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AZURE_ORG_URL_INVALID"));
    }

    @Test
    void getStatus_shouldExposeReposAccessAndLastErrorCode() throws Exception {
        when(getAzureConnectionStatusUseCase.execute()).thenReturn(new AzureConnectionStatusOutput(
                AzureConnectionStatus.ERROR, "https://login.microsoftonline.com/t/v2.0", "user-1", null, 0,
                AzureAuthMethod.OAUTH, false, "AZURE_ORG_URL_INVALID", "Informe a URL"));

        mockMvc.perform(get("/api/v1/integrations/azure/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reposAccess").value(false))
                .andExpect(jsonPath("$.lastErrorCode").value("AZURE_ORG_URL_INVALID"))
                .andExpect(jsonPath("$.lastErrorMessage").value("Informe a URL"));
    }

    @Test
    void getStatus_shouldRenderUnknownReposAccessAsNull() throws Exception {
        when(getAzureConnectionStatusUseCase.execute()).thenReturn(new AzureConnectionStatusOutput(
                AzureConnectionStatus.CONNECTED, "https://dev.azure.com/myorg", null, null, 0, AzureAuthMethod.PAT));

        mockMvc.perform(get("/api/v1/integrations/azure/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reposAccess").doesNotExist());
    }

    @Test
    void connect_shouldReturn410Moved() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/azure/connect").param("orgUrl", "https://dev.azure.com/acme"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("AZURE_CONNECT_MOVED"));
    }

    @Test
    void updateOrgUrl_shouldReturnTheRecoveredStatus() throws Exception {
        when(updateAzureOrgUrlUseCase.execute("https://dev.azure.com/acme")).thenReturn(new AzureConnectionStatusOutput(
                AzureConnectionStatus.CONNECTED, "https://dev.azure.com/acme", "user-1", null, 0, AzureAuthMethod.OAUTH,
                true, null, null));

        mockMvc.perform(put("/api/v1/integrations/azure/connection/org-url")
                        .contentType("application/json")
                        .content("{\"azureOrgUrl\":\"https://dev.azure.com/acme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.reposAccess").value(true));
    }

    @Test
    void updateOrgUrl_shouldReturn422ForAnUnreachableUrl() throws Exception {
        when(updateAzureOrgUrlUseCase.execute(any())).thenThrow(new AzureOrgUrlInvalidException("unreachable"));

        mockMvc.perform(put("/api/v1/integrations/azure/connection/org-url")
                        .contentType("application/json")
                        .content("{\"azureOrgUrl\":\"https://dev.azure.com/typo\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AZURE_ORG_URL_INVALID"));
    }

    @Test
    void connectPat_shouldReturn422WhenAzureDevOpsRejectsThePat() throws Exception {
        doThrow(new andromeda.sei.ms.feature.azure.domain.exception.AzurePatInvalidException("rejected"))
                .when(configureAzureDevOpsPatUseCase).execute(any());

        mockMvc.perform(post("/api/v1/integrations/azure/connection/pat")
                        .contentType("application/json")
                        .content("{\"adoOrganizationUrl\":\"https://dev.azure.com/myorg\",\"personalAccessToken\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AZURE_PAT_INVALID"));
    }
}

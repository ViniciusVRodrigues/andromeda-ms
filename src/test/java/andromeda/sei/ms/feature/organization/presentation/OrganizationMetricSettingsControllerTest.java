package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.MetricSettingsOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateMetricSettingsInput;
import andromeda.sei.ms.feature.organization.application.usecase.GetMetricSettingsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateMetricSettingsUseCase;
import andromeda.sei.ms.feature.organization.domain.exception.MetricSettingsInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc (padrao dos controllers deste codebase; {@code @WebMvcTest} nao funciona sob
 * Boot 4 aqui): {@code @PreAuthorize} nao e avaliado — a exigencia de {@code AZURE_CONFIGURE} no
 * PUT fica no annotation do endpoint.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationMetricSettingsControllerTest {

    private static final String URL = "/api/v1/organizations/me/metric-settings";

    @Mock private GetMetricSettingsUseCase getMetricSettingsUseCase;
    @Mock private UpdateMetricSettingsUseCase updateMetricSettingsUseCase;

    @InjectMocks
    private OrganizationMetricSettingsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_returnFlatContract_when_gettingSettings() throws Exception {
        when(getMetricSettingsUseCase.execute("org-1"))
                .thenReturn(new MetricSettingsOutput(List.of("hotfix/"), true, true));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remediationBranchPrefixes[0]").value("hotfix/"))
                .andExpect(jsonPath("$.bugLinkedCountsAsRemediation").value(true))
                .andExpect(jsonPath("$.revertCountsAsRemediation").value(true));
    }

    @Test
    void should_updateSettings_when_requestIsValid() throws Exception {
        when(updateMetricSettingsUseCase.execute(eq("org-1"), any(UpdateMetricSettingsInput.class)))
                .thenReturn(new MetricSettingsOutput(List.of("fix/prod-"), false, true));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"remediationBranchPrefixes":["fix/prod-"],"bugLinkedCountsAsRemediation":false,
                         "revertCountsAsRemediation":true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remediationBranchPrefixes[0]").value("fix/prod-"))
                .andExpect(jsonPath("$.bugLinkedCountsAsRemediation").value(false));

        ArgumentCaptor<UpdateMetricSettingsInput> captor = ArgumentCaptor.forClass(UpdateMetricSettingsInput.class);
        verify(updateMetricSettingsUseCase).execute(eq("org-1"), captor.capture());
        assertThat(captor.getValue().remediationBranchPrefixes()).containsExactly("fix/prod-");
    }

    @Test
    void should_return422_when_prefixIsBlank() throws Exception {
        when(updateMetricSettingsUseCase.execute(eq("org-1"), any(UpdateMetricSettingsInput.class)))
                .thenThrow(new MetricSettingsInvalidException("blank prefix"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"remediationBranchPrefixes":[" "],"bugLinkedCountsAsRemediation":true,
                         "revertCountsAsRemediation":true}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("METRIC_SETTINGS_INVALID"));
    }

    @Test
    void should_return400_when_fieldIsMissing() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"bugLinkedCountsAsRemediation":true,"revertCountsAsRemediation":true}
                        """))
                .andExpect(status().isBadRequest());

        verify(updateMetricSettingsUseCase, never()).execute(any(), any());
    }
}

package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.SyncSettingsOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateSyncSettingsInput;
import andromeda.sei.ms.feature.organization.application.usecase.GetSyncSettingsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateSyncSettingsUseCase;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationBackfillDaysInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
 * Standalone MockMvc (padrao dos controllers deste codebase): {@code @PreAuthorize} nao e
 * avaliado aqui — a exigencia de {@code AZURE_CONFIGURE} no PUT fica no annotation do endpoint.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationSyncSettingsControllerTest {

    @Mock private GetSyncSettingsUseCase getSyncSettingsUseCase;
    @Mock private UpdateSyncSettingsUseCase updateSyncSettingsUseCase;

    @InjectMocks
    private OrganizationSyncSettingsController controller;

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
    void shouldReturnCurrentSettings() throws Exception {
        when(getSyncSettingsUseCase.execute("org-1"))
                .thenReturn(new SyncSettingsOutput(ScheduledSyncFrequency.DAILY, 90, null));

        mockMvc.perform(get("/api/v1/organizations/me/sync-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledSync").value("DAILY"))
                .andExpect(jsonPath("$.backfillDays").value(90));
    }

    @Test
    void shouldUpdateSettings() throws Exception {
        when(updateSyncSettingsUseCase.execute(eq("org-1"), any(UpdateSyncSettingsInput.class)))
                .thenReturn(new SyncSettingsOutput(ScheduledSyncFrequency.EVERY_6_HOURS, 30, null));

        mockMvc.perform(put("/api/v1/organizations/me/sync-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledSync":"EVERY_6_HOURS","backfillDays":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledSync").value("EVERY_6_HOURS"))
                .andExpect(jsonPath("$.backfillDays").value(30));
    }

    @Test
    void shouldReturn422WhenBackfillIsOutOfRange() throws Exception {
        when(updateSyncSettingsUseCase.execute(eq("org-1"), any(UpdateSyncSettingsInput.class)))
                .thenThrow(new OrganizationBackfillDaysInvalidException(3));

        mockMvc.perform(put("/api/v1/organizations/me/sync-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledSync":"DAILY","backfillDays":3}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_BACKFILL_DAYS_INVALID"));
    }

    @Test
    void shouldReturn400WhenFrequencyIsMissing() throws Exception {
        mockMvc.perform(put("/api/v1/organizations/me/sync-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"backfillDays":90}
                                """))
                .andExpect(status().isBadRequest());

        verify(updateSyncSettingsUseCase, never()).execute(any(), any());
    }
}

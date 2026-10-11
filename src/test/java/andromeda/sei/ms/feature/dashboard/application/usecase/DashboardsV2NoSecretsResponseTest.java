package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.dashboard.presentation.OrganizationDashboardsV2Controller;
import andromeda.sei.ms.feature.dashboard.presentation.OrganizationDashboardsV2Mapper;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static andromeda.sei.ms.feature.dashboard.application.usecase.GetIntegrationsDashboardV2UseCaseTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * dashboards-customizaveis 1.12, requirement "Integrações sem segredos" — the <b>serialized</b>
 * responses of {@code /integrations} and {@code /organization}, built by the real use cases over
 * connections, provider accounts, connector tokens and users that carry distinct secret sentinels
 * (Azure PAT, OAuth access/refresh tokens, GitHub PAT, connector token hash, encrypted provider
 * tokens, password hash, invite token), contain none of them — nor the GitHub account e-mail — and
 * no secret-named key.
 */
class DashboardsV2NoSecretsResponseTest {

    private static final List<String> SECRETS = List.of(PAT, ACCESS, REFRESH, GH_PAT, TOKEN_HASH, ENC_ACCESS,
            ENC_REFRESH, PASSWORD_HASH, INVITE, GH_EMAIL);
    private static final List<String> SECRET_KEYS = List.of("\"personalAccessToken\"", "\"accessToken\"",
            "\"refreshToken\"", "\"tokenHash\"", "\"accessTokenEncrypted\"", "\"refreshTokenEncrypted\"",
            "\"passwordHash\"", "\"inviteToken\"", "\"errorDetail\"", "\"externalEmail\"");

    private final GetIntegrationsDashboardV2UseCaseTest fixtures = new GetIntegrationsDashboardV2UseCaseTest();
    private AutoCloseable mocks;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(fixtures);
        seed(fixtures);
        fixtures.azureLogs.add(log(andromeda.sei.ms.feature.integration.domain.Integration.AZURE_DEVOPS,
                andromeda.sei.ms.feature.integration.domain.SyncOperation.SYNC_WORK_ITEMS,
                andromeda.sei.ms.feature.integration.domain.SyncStatus.FAILED, "2026-10-10T08:00:00Z", 1000L, 1, 1));
        OrganizationDashboardData data = new OrganizationDashboardData(fixtures.projectRepository,
                fixtures.userRepository, mock(WorkItemRepository.class), mock(SprintRepository.class),
                new EffortBearingLevelResolver(), fixtures.bindingRepository, fixtures.repositoryRepository,
                mock(VcsReadPort.class), fixtures.commitRepository,
                new DeliveryMetricsService(mock(VcsReadPort.class), mock(WorkItemRepository.class),
                        fixtures.organizationRepository),
                fixtures.organizationRepository, mock(AIUsageRepository.class), mock(AiUsageAllocationRepository.class),
                fixtures.connectorTokenRepository, mock(FluencyScoreRepository.class),
                fixtures.azureConnectionRepository, fixtures.githubConnectionRepository,
                mock(AzureProjectBindingRepository.class), fixtures.syncLogRepository);
        CurrentUserPermissions permissions = mock(CurrentUserPermissions.class);
        EffectivePermissionService effective = mock(EffectivePermissionService.class);
        when(effective.projectIdsWith(any(), anyString())).thenReturn(Set.of("p1"));
        OrganizationDashboardsV2Controller controller = new OrganizationDashboardsV2Controller(
                new GetOrganizationDashboardV2UseCase(data, Clock.fixed(NOW, ZoneOffset.UTC)), useCase(fixtures),
                new OrganizationDashboardsV2Mapper(), mock(DataFreshnessResolver.class), permissions, effective);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContextHolder.clear();
        mocks.close();
    }

    @Test
    void should_neverSerializeASecret_inTheIntegrationsResponse() throws Exception {
        String body = mockMvc.perform(get("/api/v1/dashboards/integrations")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("CONNECTED", "ana-gh", "notebook", "ana@azure.example.com");
        assertThat(body).contains("Access token expired");
        assertThat(body).doesNotContain(SECRETS.toArray(String[]::new));
        assertThat(body).doesNotContain(SECRET_KEYS.toArray(String[]::new));
        assertThat(body).doesNotContain("stack detail with url", "upstream failed");
        // login e-mails (u-ana@example.com) never leave; the Azure identity e-mail is @azure.example.com
        assertThat(body).doesNotContain("u-ana@example.com", "u-bruno@example.com", "u-carla@example.com");
    }

    @Test
    void should_neverSerializeASecretNorAnEmail_inTheOrganizationResponse() throws Exception {
        String body = mockMvc.perform(get("/api/v1/dashboards/organization")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("CONNECTED", "Orion");
        assertThat(body).doesNotContain(SECRETS.toArray(String[]::new));
        assertThat(body).doesNotContain(SECRET_KEYS.toArray(String[]::new));
        assertThat(body).doesNotContain("@azure.example.com", "@example.com");
        // the connection error message stays in /integrations; here only its code
        assertThat(body).contains("TOKEN_EXPIRED").doesNotContain("Access token expired");
    }
}

package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Health;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.OrgStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.PortfolioRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ThroughputByProjectRow;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetIntegrationsDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetOrganizationDashboardV2UseCase;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * dashboards-customizaveis 1.11/1.12 — contract of {@code /api/v1/dashboards/organization} and
 * {@code /integrations}: the period parameter (default 90, 422 otherwise), the visible projects
 * passed exactly as {@code projectIdsWith(DASHBOARD_STRATEGIC)} returns them, {@code asOf},
 * {@code dataFreshness} and the serialized names the v2 preview reads. Standalone MockMvc;
 * {@code @PreAuthorize} is proven in {@link OrganizationDashboardsV2ControllerSecurityTest}.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationDashboardsV2ControllerTest {

    private static final String ORG = "org-1";
    private static final Instant AS_OF = Instant.parse("2026-10-11T12:00:00Z");
    private static final DurationStat EMPTY = DurationStat.empty();

    @Mock private GetOrganizationDashboardV2UseCase organizationUseCase;
    @Mock private GetIntegrationsDashboardV2UseCase integrationsUseCase;
    @Mock private DataFreshnessResolver dataFreshnessResolver;
    @Mock private CurrentUserPermissions currentUserPermissions;
    @Mock private EffectivePermissionService effectivePermissionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrganizationDashboardsV2Controller controller = new OrganizationDashboardsV2Controller(organizationUseCase,
                integrationsUseCase, new OrganizationDashboardsV2Mapper(), dataFreshnessResolver, currentUserPermissions,
                effectivePermissionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_return200WithTheOrganization_overExactlyTheVisibleProjects_defaulting90Days() throws Exception {
        UserPermissionSnapshot snapshot = mock(UserPermissionSnapshot.class);
        when(currentUserPermissions.require()).thenReturn(snapshot);
        when(effectivePermissionService.projectIdsWith(snapshot, "DASHBOARD_STRATEGIC")).thenReturn(Set.of("p-a", "p-b"));
        when(organizationUseCase.execute(ORG, Set.of("p-a", "p-b"), 90)).thenReturn(organization());
        when(dataFreshnessResolver.resolveForProjects(ORG, List.of("p-a")))
                .thenReturn(new DataFreshness(AS_OF, true));

        mockMvc.perform(get("/api/v1/dashboards/organization"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.granularity").value("semana"))
                .andExpect(jsonPath("$.kpis.itemsDone").value(4))
                .andExpect(jsonPath("$.kpis.projects").value(1))
                .andExpect(jsonPath("$.kpis.leadTimeHours.n").value(0))
                .andExpect(jsonPath("$.portfolio[0].projectId").value("p-a"))
                .andExpect(jsonPath("$.portfolio[0].health.level").value("attention"))
                .andExpect(jsonPath("$.portfolio[0].currentSprint").doesNotExist())
                .andExpect(jsonPath("$.delivery.throughputByProject[0].byProject.p-a").value(2))
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"45", "abc", "0"})
    void should_answer422_when_thePeriodIsNotAllowed(String periodDays) throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/organization").param("periodDays", periodDays))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DASHBOARD_PERIOD_INVALID"));
        verifyNoInteractions(organizationUseCase);
    }

    @Test
    void should_return200WithTheIntegrations_withTheFreshnessOfTheWholeOrganization() throws Exception {
        when(integrationsUseCase.execute(ORG)).thenReturn(new IntegrationsDashboardV2Output(null, List.of(), 3,
                List.of(), null, new IntegrationsDashboardV2Output.UnlinkedCommits(0, 0, null, List.of()), AS_OF));
        when(dataFreshnessResolver.resolveForProjects(ORG, null)).thenReturn(new DataFreshness(null, false));

        mockMvc.perform(get("/api/v1/dashboards/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertCount").value(3))
                .andExpect(jsonPath("$.unlinkedCommits.pct").doesNotExist())
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(false));
        verify(dataFreshnessResolver).resolveForProjects(any(), isNull());
    }

    private static OrganizationDashboardV2Output organization() {
        OrgStats kpis = new OrgStats(1, 3, 4, 8.0, EMPTY, EMPTY, null, 0, 0, EMPTY, BigDecimal.ZERO, 0, null, 0, 4.0, BigDecimal.ONE, BigDecimal.TEN);
        PortfolioRow row = new PortfolioRow("p-a", "Andromeda", "Core", 3, null, null, 60.0, null, 4, 8.0, EMPTY,
                null, EMPTY, 0, BigDecimal.ZERO, null, List.of(2), new Health("attention", List.of("x")), 4.0, 5.0, BigDecimal.ONE, BigDecimal.TEN);
        return new OrganizationDashboardV2Output(
                new Period(90, "2026-07-14", "2026-10-11", "2026-04-15", "2026-07-13", "semana"), kpis, kpis,
                List.of(row), new OrganizationDashboardV2Output.Delivery(
                        List.of(new ThroughputByProjectRow("2026-07-20", "20/07", Map.of("p-a", 2))), List.of(),
                        List.of()), null, null, null, null, AS_OF);
    }
}

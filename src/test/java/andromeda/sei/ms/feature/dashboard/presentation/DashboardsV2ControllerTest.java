package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetIndividualDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardV2UseCase;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * dashboards-customizaveis 1.1/1.6 — contract of {@code /api/v1/dashboards/individual|team}.
 * Standalone MockMvc ({@code @WebMvcTest} does not run under Boot 4 in this repo, so
 * {@code @PreAuthorize} is proven apart in {@link DashboardsV2ControllerSecurityTest}).
 */
@ExtendWith(MockitoExtension.class)
class DashboardsV2ControllerTest {

    private static final String ORG = "org-1";
    private static final String CALLER = "user-a";
    private static final Instant AS_OF = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant SYNC_AT = Instant.parse("2026-10-11T10:00:00Z");

    @Mock private GetIndividualDashboardV2UseCase individualUseCase;
    @Mock private GetTeamDashboardV2UseCase teamUseCase;
    @Mock private DataFreshnessResolver dataFreshnessResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DashboardsV2Controller controller = new DashboardsV2Controller(individualUseCase, teamUseCase,
                new DashboardsV2Mapper(), dataFreshnessResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        TenantContextHolder.set(ORG);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                CALLER, null, AuthorityUtils.createAuthorityList("DASHBOARD_INDIVIDUAL", "DASHBOARD_TEAM")));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return200WithIndividualPayload_asOfAndDataFreshness() throws Exception {
        when(individualUseCase.execute(ORG, CALLER, "p1", "s1")).thenReturn(individualOutput());
        when(dataFreshnessResolver.resolve(ORG, "p1")).thenReturn(new DataFreshness(SYNC_AT, false));

        mockMvc.perform(get("/api/v1/dashboards/individual").param("projectId", "p1").param("sprintId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.id").value(CALLER))
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(false))
                .andExpect(jsonPath("$.dataFreshness.oldestSyncAt").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.reviewsGiven.total").value(0))
                .andExpect(jsonPath("$.ai.allocation.pendingDays").value(0))
                .andExpect(jsonPath("$.sprint").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void should_ignoreUserIdInQuery_andReadThePersonFromTheJwt() throws Exception {
        when(individualUseCase.execute(eq(ORG), anyString(), eq("p1"), isNull())).thenReturn(individualOutput());

        mockMvc.perform(get("/api/v1/dashboards/individual").param("projectId", "p1").param("userId", "user-b")
                        .param("memberId", "user-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.id").value(CALLER));

        verify(individualUseCase).execute(ORG, CALLER, "p1", null);
    }

    @Test
    void should_answer404SprintNotFound_when_individualSprintIsOfAnotherProject() throws Exception {
        when(individualUseCase.execute(any(), any(), any(), eq("s-other"))).thenThrow(new SprintNotFoundException("s-other"));

        mockMvc.perform(get("/api/v1/dashboards/individual").param("projectId", "p1").param("sprintId", "s-other"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
    }

    @Test
    void should_return200WithTeamPayload_asOfAndDataFreshness() throws Exception {
        when(teamUseCase.execute(ORG, CALLER, "p1", "s1"))
                .thenReturn(TeamDashboardV2Output.withoutSprint(new TeamDashboardV2Output.TeamInfo("Core", 4), AS_OF));
        when(dataFreshnessResolver.resolve(ORG, "p1")).thenReturn(new DataFreshness(null, true));

        mockMvc.perform(get("/api/v1/dashboards/team").param("projectId", "p1").param("sprintId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.name").value("Core"))
                .andExpect(jsonPath("$.team.memberCount").value(4))
                .andExpect(jsonPath("$.asOf").value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(true))
                .andExpect(jsonPath("$.statusByType").isArray())
                .andExpect(jsonPath("$.agingItems").isArray())
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.stalePrs").isArray())
                .andExpect(jsonPath("$.repos").isArray())
                .andExpect(jsonPath("$.commitsByDay").isArray())
                .andExpect(jsonPath("$.ai.membersWithUsage").value(0))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void should_answer404SprintNotFound_when_teamSprintIsOfAnotherProject() throws Exception {
        when(teamUseCase.execute(any(), any(), any(), eq("s-other"))).thenThrow(new SprintNotFoundException("s-other"));

        mockMvc.perform(get("/api/v1/dashboards/team").param("projectId", "p1").param("sprintId", "s-other"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
    }

    @Test
    void should_serializeBooleanFlags_withThePreviewNames_andDurationsAsP50P90N() throws Exception {
        var stat = new andromeda.sei.ms.shared.domain.metrics.DurationStat(8.0, 16.0, 2);
        var history = new TeamDashboardV2Output.HistoryPoint("s1", "S1*", "Sprint 1", true, true, 10.0, 8.0, 80.0,
                stat, java.math.BigDecimal.ONE, null);
        var member = new TeamDashboardV2Output.Member(CALLER, "Ana", true, true, true, 1, 2, 5.0, 3.0, 1, stat,
                1, 0, null, null, null);
        var empty = TeamDashboardV2Output.withoutSprint(new TeamDashboardV2Output.TeamInfo("Core", 1), AS_OF);
        when(teamUseCase.execute(ORG, CALLER, "p1", null)).thenReturn(new TeamDashboardV2Output(null, empty.team(),
                null, null, java.util.List.of(), java.util.List.of(), java.util.List.of(), null,
                java.util.List.of(member), java.util.List.of(), java.util.List.of(), java.util.List.of(), empty.ai(),
                java.util.List.of(history), AS_OF));

        mockMvc.perform(get("/api/v1/dashboards/team").param("projectId", "p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].isSelected").value(true))
                .andExpect(jsonPath("$.history[0].isCurrent").value(true))
                .andExpect(jsonPath("$.history[0].predictabilityPct").value(80.0))
                .andExpect(jsonPath("$.history[0].cycleTimeHours.p50").value(8.0))
                .andExpect(jsonPath("$.history[0].cycleTimeHours.p90").value(16.0))
                .andExpect(jsonPath("$.history[0].cycleTimeHours.n").value(2))
                .andExpect(jsonPath("$.members[0].isMe").value(true))
                .andExpect(jsonPath("$.members[0].me").doesNotExist())
                .andExpect(jsonPath("$.history[0].selected").doesNotExist())
                .andExpect(jsonPath("$.members[0].reviewsGiven").value(org.hamcrest.Matchers.nullValue()));
    }

    private static IndividualDashboardV2Output individualOutput() {
        return IndividualDashboardV2Output.withoutSprint(
                new IndividualDashboardV2Output.Person(CALLER, "Ana", true, true, false), AS_OF);
    }
}

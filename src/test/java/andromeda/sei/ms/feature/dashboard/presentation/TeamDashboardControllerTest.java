package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.TeamDashboardResponse;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TeamDashboardControllerTest {

    @Mock GetTeamDashboardUseCase getTeamDashboardUseCase;
    @Mock DataFreshnessResolver dataFreshnessResolver;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    TeamDashboardMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TeamDashboardMapper();
        TeamDashboardController controller = new TeamDashboardController(getTeamDashboardUseCase, mapper, dataFreshnessResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        TenantContextHolder.set("org-1");

        AndromedaUserDetails user = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-1")
                .permissions(List.of("DASHBOARD_TEAM"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return200WithTeamDashboard_when_projectIdProvided() throws Exception {
        TeamDashboardOutput output = new TeamDashboardOutput(
                "sprint-1", "Sprint 1", "2026-06-01", "2026-06-14",
                10.0, 4.5, 8.0, new BigDecimal("25.00"),
                List.of(), List.of(new TeamDashboardOutput.MemberMetrics("u1", "Dev", "DE", 5.0, 4.0, 6.0,
                        new BigDecimal("10.00"), null, 2,
                        new andromeda.sei.ms.shared.domain.metrics.DurationStat(4.0, 9.0, 2),
                        new andromeda.sei.ms.shared.domain.metrics.DurationStat(6.0, 12.0, 2),
                        new BigDecimal("5.00"))), List.of(),
                new andromeda.sei.ms.shared.domain.metrics.DurationStat(3.0, 100.0, 5),
                new andromeda.sei.ms.shared.domain.metrics.DurationStat(8.0, 20.0, 5),
                andromeda.sei.ms.feature.vcs.domain.ReviewMetrics.empty(), new BigDecimal("7.50"));

        when(getTeamDashboardUseCase.execute(eq("org-1"), any(), eq("project-1"), isNull(), isNull(), isNull()))
                .thenReturn(output);

        mockMvc.perform(get("/api/v1/dashboard/team").param("projectId", "project-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprintId").value("sprint-1"))
                .andExpect(jsonPath("$.teamVelocity").value(10.0))
                .andExpect(jsonPath("$.cycleTime.p50").value(3.0))
                .andExpect(jsonPath("$.cycleTime.p90").value(100.0))
                .andExpect(jsonPath("$.cycleTime.n").value(5))
                .andExpect(jsonPath("$.leadTime.p50").value(8.0))
                .andExpect(jsonPath("$.review.slaHours").value(24))
                .andExpect(jsonPath("$.review.eligiblePrs").value(0))
                .andExpect(jsonPath("$.unallocatedAiCostUsd").value(7.5))
                .andExpect(jsonPath("$.members[0].cycleTime.p90").value(9.0))
                .andExpect(jsonPath("$.members[0].unallocatedAiCostUsd").value(5.0))
                .andExpect(jsonPath("$.members[0].aiCostUsd").value(10.0))
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.velocityTrend").isArray());
    }

    @Test
    void should_return400_when_projectIdMissing() throws Exception {
        when(getTeamDashboardUseCase.execute(any(), any(), isNull(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("TEAM_DASHBOARD_PROJECT_REQUIRED", "projectId is required"));

        mockMvc.perform(get("/api/v1/dashboard/team"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_includeOldestSyncAt_when_projectDataIsStale() throws Exception {
        java.time.Instant threeDaysAgo = java.time.Instant.parse("2026-09-27T12:00:00Z");
        TeamDashboardOutput output = new TeamDashboardOutput(
                "sprint-1", "Sprint 1", "2026-06-01", "2026-06-14",
                10.0, 4.5, 8.0, new BigDecimal("25.00"),
                List.of(), List.of(), List.of(), null, null, null, null);
        when(getTeamDashboardUseCase.execute(eq("org-1"), any(), eq("project-1"), isNull(), isNull(), isNull()))
                .thenReturn(output);
        when(dataFreshnessResolver.resolve("org-1", "project-1"))
                .thenReturn(new andromeda.sei.ms.feature.sync.application.dto.DataFreshness(threeDaysAgo, false));

        mockMvc.perform(get("/api/v1/dashboard/team").param("projectId", "project-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataFreshness.oldestSyncAt").value("2026-09-27T12:00:00Z"))
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(false));
    }
}

package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetDeveloperDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.DeveloperDashboardResponse;
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
class DeveloperDashboardControllerTest {

    @Mock GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;
    @Mock DataFreshnessResolver dataFreshnessResolver;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    DeveloperDashboardMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DeveloperDashboardMapper();
        DeveloperDashboardController controller = new DeveloperDashboardController(getDeveloperDashboardUseCase, mapper, dataFreshnessResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        TenantContextHolder.set("org-1");

        AndromedaUserDetails user = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-1")
                .permissions(List.of("DASHBOARD_INDIVIDUAL"))
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
    void should_return200WithDashboardData_when_authenticated() throws Exception {
        DeveloperDashboardOutput output = new DeveloperDashboardOutput(
                List.of(), null, null, null, null, List.of(), BigDecimal.ZERO, null, null, null,
                List.of(), 0, List.of(), null, null, null, List.of());
        DeveloperDashboardResponse response = mapper.toResponse(output);

        when(getDeveloperDashboardUseCase.execute(
                eq("org-1"), eq("user-1"), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(output);

        mockMvc.perform(get("/api/v1/dashboard/developer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSprintItems").isArray())
                .andExpect(jsonPath("$.tokensByDay").isArray());
    }

    @Test
    void should_return200_when_queryParamsProvided() throws Exception {
        DeveloperDashboardOutput output = new DeveloperDashboardOutput(
                List.of(), 4.5, 8.0, 5.0, 10.0, List.of(), new BigDecimal("12.50"), 250.0, null, null,
                List.of(), 0, List.of(), null, null, null, List.of());

        when(getDeveloperDashboardUseCase.execute(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(output);

        mockMvc.perform(get("/api/v1/dashboard/developer")
                        .param("sprintId", "sprint-123")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgCycleTimeHours").value(4.5))
                .andExpect(jsonPath("$.storyPointsDelivered").value(5.0))
                .andExpect(jsonPath("$.totalSpInSprint").value(10.0))
                .andExpect(jsonPath("$.avgTokensPerSp").value(250.0));
    }
}

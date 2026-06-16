package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetStrategicDashboardUseCase;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
class StrategicDashboardControllerTest {

    @Mock GetStrategicDashboardUseCase getStrategicDashboardUseCase;

    MockMvc mockMvc;
    ObjectMapper objectMapper;
    StrategicDashboardMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new StrategicDashboardMapper(new DeveloperDashboardMapper());
        StrategicDashboardController controller = new StrategicDashboardController(getStrategicDashboardUseCase, mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        TenantContextHolder.set("org-1");

        AndromedaUserDetails user = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-1")
                .permissions(List.of("DASHBOARD_STRATEGIC"))
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
    void should_return200_when_orgScope() throws Exception {
        StrategicDashboardOutput output = new StrategicDashboardOutput("ORG", List.of(), null, null);

        when(getStrategicDashboardUseCase.execute(eq("org-1"), any(), isNull(), isNull(), any(), any()))
                .thenReturn(output);

        mockMvc.perform(get("/api/v1/dashboard/strategic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("ORG"))
                .andExpect(jsonPath("$.projects").isArray());
    }

    @Test
    void should_return200_when_projectScope() throws Exception {
        StrategicDashboardOutput.DoraMetrics dora = new StrategicDashboardOutput.DoraMetrics(
                12.0, "HIGH", 2.0, "HIGH", 5.0, "ELITE", null, true);
        StrategicDashboardOutput.ProjectDetail detail = new StrategicDashboardOutput.ProjectDetail(
                "project-1", "My Project", List.of(), dora);
        StrategicDashboardOutput output = new StrategicDashboardOutput("PROJECT", null, detail, null);

        when(getStrategicDashboardUseCase.execute(eq("org-1"), any(), eq("project-1"), isNull(), any(), any()))
                .thenReturn(output);

        mockMvc.perform(get("/api/v1/dashboard/strategic").param("projectId", "project-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("PROJECT"))
                .andExpect(jsonPath("$.projectDetail.projectId").value("project-1"))
                .andExpect(jsonPath("$.projectDetail.dora.estimated").value(true));
    }

    @Test
    void should_return200_when_memberScope() throws Exception {
        DeveloperDashboardOutput devOutput = new DeveloperDashboardOutput(
                List.of(), null, null, 5.0, List.of(), BigDecimal.ZERO, null, null, null,
                List.of(), 0, List.of(), null);
        StrategicDashboardOutput output = new StrategicDashboardOutput("MEMBER", null, null, devOutput);

        when(getStrategicDashboardUseCase.execute(eq("org-1"), any(), eq("project-1"), eq("member-1"), any(), any()))
                .thenReturn(output);

        mockMvc.perform(get("/api/v1/dashboard/strategic")
                        .param("projectId", "project-1")
                        .param("memberId", "member-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("MEMBER"))
                .andExpect(jsonPath("$.memberDetail.storyPointsDelivered").value(5.0));
    }
}

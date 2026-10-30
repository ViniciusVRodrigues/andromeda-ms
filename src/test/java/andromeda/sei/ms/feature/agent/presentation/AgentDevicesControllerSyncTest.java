package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.SyncRequestOutput;
import andromeda.sei.ms.feature.agent.application.usecase.GetAgentMetaUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.ListOwnDevicesUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RequestDeviceSyncUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RevokeOwnDeviceUseCase;
import andromeda.sei.ms.feature.agent.domain.exception.AgentDeviceNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/v1/agent/devices/{id}/sync, through method security (the controller bean is proxied). */
class AgentDevicesControllerSyncTest {

    private static final RequestDeviceSyncUseCase SYNC = mock(RequestDeviceSyncUseCase.class);
    private static AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean AgentDevicesController controller() {
            return new AgentDevicesController(mock(ListOwnDevicesUseCase.class), mock(RevokeOwnDeviceUseCase.class),
                    mock(GetAgentMetaUseCase.class), SYNC);
        }
    }

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @BeforeEach
    void setUp() {
        reset(SYNC);
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(AgentDevicesController.class))
                .setControllerAdvice(new AgentExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWith(String... permissions) {
        AndromedaUserDetails user = AndromedaUserDetails.builder().userId("user-1").organizationId("org-1")
                .permissions(Arrays.asList(permissions)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void should_answer202WithTheRequest() throws Exception {
        authenticateWith("CONNECTOR_TOKEN_MANAGE_OWN");
        Instant at = Instant.parse("2026-10-29T18:00:01.123Z");
        when(SYNC.execute("org-1", "user-1", false, "dev-1")).thenReturn(new SyncRequestOutput("dev-1", at, null));
        mvc.perform(post("/api/v1/agent/devices/dev-1/sync"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deviceId").value("dev-1"))
                .andExpect(jsonPath("$.syncRequestedAt").exists())
                .andExpect(jsonPath("$.syncServedAt").doesNotExist());
    }

    @Test
    void should_passCanManageAllFromTheAuthority() throws Exception {
        authenticateWith("CONNECTOR_TOKEN_MANAGE_ALL");
        when(SYNC.execute("org-1", "user-1", true, "dev-1"))
                .thenReturn(new SyncRequestOutput("dev-1", Instant.parse("2026-10-29T18:00:01Z"), null));
        mvc.perform(post("/api/v1/agent/devices/dev-1/sync")).andExpect(status().isAccepted());
        verify(SYNC).execute("org-1", "user-1", true, "dev-1");
    }

    @Test
    void should_answer404WhenTheDeviceIsNotFound() throws Exception {
        authenticateWith("CONNECTOR_TOKEN_MANAGE_OWN");
        when(SYNC.execute("org-1", "user-1", false, "nope")).thenThrow(new AgentDeviceNotFoundException("nope"));
        mvc.perform(post("/api/v1/agent/devices/nope/sync"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_DEVICE_NOT_FOUND"));
    }

    @Test
    void should_answer403WithoutEitherAuthority() throws Exception {
        authenticateWith("DASHBOARD_INDIVIDUAL");
        mvc.perform(post("/api/v1/agent/devices/dev-1/sync")).andExpect(status().isForbidden());
        verifyNoInteractions(SYNC);
    }
}

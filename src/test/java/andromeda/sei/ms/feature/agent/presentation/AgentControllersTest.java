package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.TokenPairOutput;
import andromeda.sei.ms.feature.agent.application.usecase.ApprovePairingUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.DenyPairingUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.ExchangeDeviceTokenUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.GetPairingUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RefreshAgentTokenUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RequestDeviceCodeUseCase;
import andromeda.sei.ms.feature.agent.domain.exception.AgentOAuthException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentPairingConflictException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRateLimitedException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentVersionUnsupportedException;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllersTest {

    private RequestDeviceCodeUseCase requestCode;
    private ExchangeDeviceTokenUseCase exchange;
    private RefreshAgentTokenUseCase refresh;
    private MockMvc mvc;

    private static final String CODE_BODY = "{\"agentVersion\":\"0.2.0\",\"machineName\":\"NOTE\",\"os\":\"windows\","
            + "\"osLabel\":\"Windows 11\",\"arch\":\"x64\",\"timezone\":\"America/Sao_Paulo\"}";

    @BeforeEach
    void setUp() {
        requestCode = mock(RequestDeviceCodeUseCase.class);
        exchange = mock(ExchangeDeviceTokenUseCase.class);
        refresh = mock(RefreshAgentTokenUseCase.class);
        AgentDeviceAuthController controller = new AgentDeviceAuthController(requestCode, exchange, refresh,
                new RateLimiter(), new ClientIpResolver(), "https://app.example.com/");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test
    void should_returnVerificationUrisPointingToTheParearScreen() throws Exception {
        when(requestCode.execute(any())).thenReturn(new DeviceCodeOutput("adc_x", "BCDF-GHJK", 600, 5));
        mvc.perform(post("/agent/v1/device/code").contentType(MediaType.APPLICATION_JSON).content(CODE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_code").value("adc_x"))
                .andExpect(jsonPath("$.user_code").value("BCDF-GHJK"))
                .andExpect(jsonPath("$.verification_uri").value("https://app.example.com/parear"))
                .andExpect(jsonPath("$.verification_uri_complete").value("https://app.example.com/parear?codigo=BCDF-GHJK"))
                .andExpect(jsonPath("$.expires_in").value(600))
                .andExpect(jsonPath("$.interval").value(5));
    }

    @Test
    void should_answer429WithRetryAfterOnTheEleventhCodeRequestFromTheSameIp() throws Exception {
        when(requestCode.execute(any())).thenReturn(new DeviceCodeOutput("adc_x", "BCDF-GHJK", 600, 5));
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/agent/v1/device/code").contentType(MediaType.APPLICATION_JSON).content(CODE_BODY))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/agent/v1/device/code").contentType(MediaType.APPLICATION_JSON).content(CODE_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AGENT_RATE_LIMITED"));
    }

    @Test
    void should_answer426WithMinVersionWhenTheVersionIsTooOld() throws Exception {
        when(requestCode.execute(any())).thenThrow(new AgentVersionUnsupportedException("0.1.0"));
        mvc.perform(post("/agent/v1/device/code").contentType(MediaType.APPLICATION_JSON).content(CODE_BODY))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.code").value("AGENT_VERSION_UNSUPPORTED"))
                .andExpect(jsonPath("$.minVersion").value("0.1.0"));
    }

    @Test
    void should_exchangeTheDeviceCodeFromJsonAndFromForm() throws Exception {
        when(exchange.execute("adc_x")).thenReturn(new TokenPairOutput("adt_a", "adr_r", 3600,
                "usage:write tasks:read", "dev-1", "acme"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"urn:ietf:params:oauth:grant-type:device_code\",\"device_code\":\"adc_x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("adt_a"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.refresh_token").value("adr_r"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.scope").value("usage:write tasks:read"))
                .andExpect(jsonPath("$.device_id").value("dev-1"))
                .andExpect(jsonPath("$.organization_slug").value("acme"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "urn:ietf:params:oauth:grant-type:device_code").param("device_code", "adc_x"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").value("adt_a"));
    }

    @Test
    void should_refreshWithTheRefreshTokenGrant() throws Exception {
        when(refresh.execute("adr_r")).thenReturn(new TokenPairOutput("adt_b", "adr_s", 3600, "s", "dev-1", "acme"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token").param("refresh_token", "adr_r"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refresh_token").value("adr_s"));
    }

    @Test
    void should_answerTheRfcErrorBodyOn400() throws Exception {
        when(exchange.execute(any())).thenThrow(new AgentOAuthException("authorization_pending", "Aguardando"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"device_code\",\"device_code\":\"adc_x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("authorization_pending"))
                .andExpect(jsonPath("$.error_description").value("Aguardando"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"password\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/agent/v1/device/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"refresh_token\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void should_answer409WithTheStatusInTheBodyForPairingConflicts() throws Exception {
        ApprovePairingUseCase approve = mock(ApprovePairingUseCase.class);
        when(approve.execute(any(), any(), eq("BCDF-GHJK"))).thenThrow(AgentPairingConflictException.expired());
        AgentPairingController controller = new AgentPairingController(mock(GetPairingUseCase.class), approve,
                mock(DenyPairingUseCase.class), new RateLimiter(), new ClientIpResolver());
        MockMvc pairing = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgentExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        var user = mock(AndromedaUserDetails.class);
        when(user.getOrganizationId()).thenReturn("org-1");
        when(user.getUserId()).thenReturn("user-1");
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user, null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            pairing.perform(post("/api/v1/agent/pairing/BCDF-GHJK/approve"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AGENT_PAIRING_EXPIRED"))
                    .andExpect(jsonPath("$.status").value("EXPIRED"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void should_rateLimitPairingLookupsPerUserAt10PerMinute() {
        GetPairingUseCase get = mock(GetPairingUseCase.class);
        AgentPairingController controller = new AgentPairingController(get, mock(ApprovePairingUseCase.class),
                mock(DenyPairingUseCase.class), new RateLimiter(), new ClientIpResolver());
        var user = mock(AndromedaUserDetails.class);
        when(user.getUserId()).thenReturn("user-1");
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        for (int i = 0; i < 10; i++) {
            controller.get(user, "BCDF-GHJK", request);
        }
        assertThatThrownBy(() -> controller.get(user, "BCDF-GHJK", request))
                .isInstanceOfSatisfying(AgentRateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isPositive());
    }
}

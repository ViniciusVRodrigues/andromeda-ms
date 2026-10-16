package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.usecase.CompleteAccountUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.ConfirmEmailVerificationUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.ConfirmPasswordResetUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.GetCurrentUserUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.LoginUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.RequestPasswordResetUseCase;
import andromeda.sei.ms.feature.auth.application.usecase.SendEmailVerificationUseCase;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerEmailVerificationRateLimitTest {

    private static final String SEND = "/api/v1/auth/email-verification";
    private static final String CONFIRM = "/api/v1/auth/email-verification/confirm";

    @Mock private SendEmailVerificationUseCase sendUseCase;
    @Mock private ConfirmEmailVerificationUseCase confirmUseCase;
    @Mock private RequestPasswordResetUseCase requestPasswordResetUseCase;
    @Mock private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
    @Mock private LoginUseCase loginUseCase;
    @Mock private CompleteAccountUseCase completeAccountUseCase;
    @Mock private GetCurrentUserUseCase getCurrentUserUseCase;
    @Mock private AuthPresentationMapper mapper;
    @Mock private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(requestPasswordResetUseCase, confirmPasswordResetUseCase,
                new RateLimiter(), new ClientIpResolver(), loginUseCase, completeAccountUseCase,
                getCurrentUserUseCase, sendUseCase, confirmUseCase, mapper, jwtService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions send(String email, String ip) throws Exception {
        return mockMvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip).content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions confirm(String email, String ip) throws Exception {
        return mockMvc.perform(post(CONFIRM).contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip).content("{\"email\":\"%s\",\"code\":\"123456\"}".formatted(email)));
    }

    private void expectRefused(ResultActions r) throws Exception {
        r.andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void should_refuseThe11thSendFromTheSameIp() throws Exception {
        for (int i = 0; i < 10; i++) send("a" + i + "@x.com", "1.1.1.1").andExpect(status().isOk());
        expectRefused(send("a10@x.com", "1.1.1.1"));
        verify(sendUseCase, times(10)).execute(anyString());
        send("other@x.com", "2.2.2.2").andExpect(status().isOk());
    }

    @Test
    void should_refuseThe6thSendToTheSameEmail_fromDifferentIps() throws Exception {
        for (int i = 0; i < 5; i++) send("alvo@x.com", "3.3.3." + i).andExpect(status().isOk());
        expectRefused(send("alvo@x.com", "3.3.3.99"));
        verify(sendUseCase, times(5)).execute("alvo@x.com");
    }

    @Test
    void should_countEmailWithCaseInTheSameBucket_onSend() throws Exception {
        send("alvo@x.com", "4.4.4.1").andExpect(status().isOk());
        send("ALVO@x.com", "4.4.4.2").andExpect(status().isOk());
        send("alvo@x.com", "4.4.4.3").andExpect(status().isOk());
        send("Alvo@X.com", "4.4.4.4").andExpect(status().isOk());
        send("alvo@x.com", "4.4.4.5").andExpect(status().isOk());
        expectRefused(send("ALVO@X.COM", "4.4.4.6"));
        verify(sendUseCase, times(5)).execute(anyString());
    }

    @Test
    void should_refuseThe21stConfirmFromTheSameIp() throws Exception {
        for (int i = 0; i < 20; i++) confirm("c" + i + "@x.com", "5.5.5.5").andExpect(status().isOk());
        expectRefused(confirm("c20@x.com", "5.5.5.5"));
        verify(confirmUseCase, times(20)).execute(anyString(), anyString());
    }

    @Test
    void should_refuseThe11thConfirmForTheSameEmail_fromDifferentIps_withoutCallingTheUseCase() throws Exception {
        for (int i = 0; i < 10; i++) confirm("alvo@x.com", "6.6.6." + i).andExpect(status().isOk());
        expectRefused(confirm("alvo@x.com", "6.6.6.99"));
        verify(confirmUseCase, times(10)).execute("alvo@x.com", "123456");
    }

    @Test
    void should_countEmailWithCaseInTheSameBucket_onConfirm() throws Exception {
        for (int i = 0; i < 10; i++) {
            confirm(i % 2 == 0 ? "ALVO@x.com" : "Alvo@x.com", "7.7.7." + i).andExpect(status().isOk());
        }
        expectRefused(confirm("Alvo@X.com", "7.7.7.99"));
        verify(confirmUseCase, times(10)).execute(anyString(), anyString());
        verify(confirmUseCase, never()).execute("Alvo@X.com", "123456");
    }

    @Test
    void should_keepSendAndConfirmBucketsIndependent() throws Exception {
        for (int i = 0; i < 5; i++) send("alvo@x.com", "8.8.8." + i).andExpect(status().isOk());
        expectRefused(send("alvo@x.com", "8.8.8.50"));
        confirm("alvo@x.com", "8.8.8.60").andExpect(status().isOk());
    }
}

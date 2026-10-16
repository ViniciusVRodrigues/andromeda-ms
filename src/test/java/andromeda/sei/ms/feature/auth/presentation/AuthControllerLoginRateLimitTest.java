package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.CompleteAccountOutput;
import andromeda.sei.ms.feature.auth.application.dto.LoginOutput;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerLoginRateLimitTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String INVITE = "/api/v1/auth/invite/complete";

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
        lenient().when(loginUseCase.execute(any())).thenReturn(new LoginOutput("u", "o", List.of(), 1L));
        lenient().when(completeAccountUseCase.execute(any()))
                .thenReturn(new CompleteAccountOutput("u", "o", List.of(), 1L));
    }

    private ResultActions login(String email, String ip) throws Exception {
        return mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip).content("{\"email\":\"%s\",\"password\":\"x\"}".formatted(email)));
    }

    private ResultActions invite(String ip) throws Exception {
        return mockMvc.perform(post(INVITE).contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip)
                .content("{\"token\":\"t\",\"name\":\"n\",\"password\":\"p\"}"));
    }

    private void expectRefused(ResultActions r) throws Exception {
        r.andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void should_refuseThe31stLoginFromTheSameIp() throws Exception {
        for (int i = 0; i < 30; i++) login("u" + i + "@x.com", "1.1.1.1").andExpect(status().isOk());
        expectRefused(login("u30@x.com", "1.1.1.1"));
        verify(loginUseCase, times(30)).execute(any());
        login("outro@x.com", "2.2.2.2").andExpect(status().isOk());
    }

    @Test
    void should_refuseThe11thLoginForTheSameEmail_fromDifferentIps_withoutCallingTheUseCase() throws Exception {
        for (int i = 0; i < 10; i++) login("alvo@x.com", "3.3.3." + i).andExpect(status().isOk());
        expectRefused(login("alvo@x.com", "3.3.3.99"));
        verify(loginUseCase, times(10)).execute(any());
    }

    @Test
    void should_countEmailWithCaseInTheSameBucket_onLogin() throws Exception {
        for (int i = 0; i < 10; i++) {
            login(i % 2 == 0 ? "ALVO@x.com" : "Alvo@x.com", "4.4.4." + i).andExpect(status().isOk());
        }
        expectRefused(login("alvo@X.com", "4.4.4.99"));
        verify(loginUseCase, times(10)).execute(any());
    }

    @Test
    void should_refuseThe21stInviteCompleteFromTheSameIp() throws Exception {
        for (int i = 0; i < 20; i++) invite("5.5.5.5").andExpect(status().isOk());
        expectRefused(invite("5.5.5.5"));
        verify(completeAccountUseCase, times(20)).execute(any());
        invite("6.6.6.6").andExpect(status().isOk());
    }

    @Test
    void should_keepLoginAndInviteBucketsIndependent() throws Exception {
        for (int i = 0; i < 30; i++) login("v" + i + "@x.com", "7.7.7.7").andExpect(status().isOk());
        expectRefused(login("v30@x.com", "7.7.7.7"));
        invite("7.7.7.7").andExpect(status().isOk());
        verify(completeAccountUseCase, times(1)).execute(any());
    }
}

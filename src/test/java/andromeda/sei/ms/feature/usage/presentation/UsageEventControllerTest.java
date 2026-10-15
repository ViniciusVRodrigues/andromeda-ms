package andromeda.sei.ms.feature.usage.presentation;

import andromeda.sei.ms.feature.usage.application.usecase.RecordUsageEventsUseCase;
import andromeda.sei.ms.feature.usage.domain.UsageEventDraft;
import andromeda.sei.ms.feature.usage.domain.UsageEventInvalidException;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsageEventControllerTest {

    private static final String URL = "/api/v1/usage-events";
    private static final String SESSION = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";
    private static final String VIEW = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    @Mock private RecordUsageEventsUseCase useCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UsageEventController controller = new UsageEventController(useCase, new RateLimiter());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        TenantContextHolder.set("org-A");
        AndromedaUserDetails principal = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-A").permissions(List.of("DASHBOARD_INDIVIDUAL")).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("DASHBOARD_INDIVIDUAL"))));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private static String pageView(String screen) {
        return """
                {"type":"PAGE_VIEW","occurredAt":"%s","sessionId":"%s","viewId":"%s","screen":"%s","viewport":"lg"}"""
                .formatted(Instant.now(), SESSION, VIEW, screen);
    }

    private static String body(String... events) {
        return "{\"events\":[" + String.join(",", events) + "]}";
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_return202WithoutBody_andPassIdentityFromTokenAndTenant_ignoringTheBody() throws Exception {
        String hostile = """
                {"type":"PAGE_VIEW","occurredAt":"%s","sessionId":"%s","viewId":"%s","screen":"backlog",
                 "userId":"outro","organizationId":"B","message":"falhou para joao@x.com"}""".formatted(Instant.now(), SESSION, VIEW);

        send(body(hostile, pageView("projetos"), pageView("perfil")))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        ArgumentCaptor<List<UsageEventDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(useCase).execute(eq("org-A"), eq("user-1"), drafts.capture());
        assertThat(drafts.getValue()).hasSize(3);
        assertThat(drafts.getValue().get(0).type()).isEqualTo(UsageEventType.PAGE_VIEW);
    }

    @Test
    void should_return400_when_batchIsEmptyOrHasMoreThan50() throws Exception {
        send("{\"events\":[]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send("{}").andExpect(status().isBadRequest());
        String fiftyOne = IntStream.range(0, 51).mapToObj(i -> pageView("backlog")).collect(Collectors.joining(","));
        send(body(fiftyOne)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(useCase, never()).execute(any(), any(), anyList());
    }

    @Test
    void should_accept50Events() throws Exception {
        String fifty = IntStream.range(0, 50).mapToObj(i -> pageView("backlog")).collect(Collectors.joining(","));
        send(body(fifty)).andExpect(status().isAccepted());
    }

    @Test
    void should_return400_when_clientSendsLogin() throws Exception {
        String login = pageView("backlog").replace("PAGE_VIEW", "LOGIN");
        send(body(login)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(useCase, never()).execute(any(), any(), anyList());
    }

    @Test
    void should_return400_when_typeIsUnknown() throws Exception {
        send(body(pageView("backlog").replace("PAGE_VIEW", "HOVER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void should_return400_when_screenCarriesAnIdentifier() throws Exception {
        send(body(pageView("dashboard/64f1a2b3c4d5e6f7a8b9c0d1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(body(pageView("Dashboard"))).andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_idsAreNotUuid_orJsonIsMalformed() throws Exception {
        send(body(pageView("backlog").replace(SESSION, "abc"))).andExpect(status().isBadRequest());
        send("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void should_return400_when_errorStatusIsOutOfRange_orCodeIsLowercase() throws Exception {
        String base = """
                {"type":"ERROR","occurredAt":"%s","sessionId":"%s","viewId":"%s","screen":"backlog","origin":"api","status":%s,"code":"%s"}""";
        send(body(base.formatted(Instant.now(), SESSION, VIEW, "700", "X_ERR"))).andExpect(status().isBadRequest());
        send(body(base.formatted(Instant.now(), SESSION, VIEW, "500", "azure_sync"))).andExpect(status().isBadRequest());
    }

    @Test
    void should_return422_when_useCaseRejectsTheBatchAsIncoherent() throws Exception {
        doThrow(new UsageEventInvalidException("SCREEN_TIME precisa de visibleMs"))
                .when(useCase).execute(any(), any(), anyList());

        send(body(pageView("backlog")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("USAGE_EVENT_INVALID"));
    }

    @Test
    void should_return429WithRetryAfter_onThe121stRequestOfTheSameUser() throws Exception {
        for (int i = 0; i < 120; i++) {
            send(body(pageView("backlog"))).andExpect(status().isAccepted());
        }

        send(body(pageView("backlog")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("USAGE_EVENTS_RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        verify(useCase, times(120)).execute(any(), any(), anyList());
    }
}

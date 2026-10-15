package andromeda.sei.ms.feature.usage.presentation;

import andromeda.sei.ms.feature.usage.application.FunnelEventQuota;
import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.application.usecase.RecordFunnelEventsUseCase;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.infrastructure.web.ClientIpResolver;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FunnelEventControllerTest {

    private static final String URL = "/api/v1/usage-events/public";
    private static final String FUNNEL = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";

    @Mock private FunnelEventRepository repository;

    private MockMvc mockMvc;

    private void build(boolean enabled) {
        RecordFunnelEventsUseCase useCase = new RecordFunnelEventsUseCase(
                new TelemetrySettings(enabled), new FunnelEventQuota(), repository);
        mockMvc = MockMvcBuilders.standaloneSetup(new FunnelEventController(useCase, new RateLimiter(), new ClientIpResolver()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void setUp() {
        build(true);
    }

    private static String ev(String stage, String step) {
        return "{\"stage\":\"%s\",\"occurredAt\":\"%s\"%s}".formatted(stage, Instant.now(),
                step == null ? "" : ",\"step\":\"" + step + "\"");
    }

    private static String body(String flow, String... events) {
        return "{\"funnelId\":\"%s\",\"flow\":\"%s\",\"events\":[%s]}".formatted(FUNNEL, flow, String.join(",", events));
    }

    private ResultActions send(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_answer202WithoutBody_andStoreAnonymousEvents_withNoAuthentication() throws Exception {
        send(body("signup", ev("OPEN", null), ev("STEP_ENTER", "pessoal")))
                .andExpect(status().isAccepted()).andExpect(content().string(""));

        ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(e -> {
            assertThat(e.getOrganizationId()).isNull();
            assertThat(e.getUserId()).isNull();
            assertThat(e.getFlow()).isEqualTo(FunnelFlow.SIGNUP);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_neverPersistTheIpNorTheUserAgent() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.77, 10.0.0.1")
                        .header("User-Agent", "Mozilla/5.0 SecretAgent")
                        .with(r -> { r.setRemoteAddr("198.51.100.9"); return r; })
                        .content(body("signup", ev("OPEN", null))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(captor.capture());
        String dump = captor.getValue().stream().map(FunnelControllerDump::of).collect(Collectors.joining("|"));
        assertThat(dump).doesNotContain("203.0.113.77").doesNotContain("198.51.100.9").doesNotContain("SecretAgent");
    }

    @Test
    void should_answer400_when_theClientSendsComplete() throws Exception {
        send(body("signup", ev("COMPLETE", "confirmar")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(repository, never()).insertAll(anyList());
    }

    @Test
    void should_answer400_when_theStepBelongsToAnotherFlow() throws Exception {
        send(body("invite", ev("STEP_ENTER", "projeto")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(body("signup", ev("STEP_ENTER", "bem-vindo"))).andExpect(status().isBadRequest());
        send(body("signup", ev("STEP_ENTER", null))).andExpect(status().isBadRequest());
        verify(repository, never()).insertAll(anyList());
    }

    @Test
    void should_answer400_forIncoherentOrMalformedEvents() throws Exception {
        send(body("signup", ev("STEP_TIME", "senha"))).andExpect(status().isBadRequest());           // no visibleMs
        send(body("invite", ev("VERIFY_FAIL", "conta"))).andExpect(status().isBadRequest());        // VERIFY only signup
        send(body("signup", ev("NOPE", "senha"))).andExpect(status().isBadRequest());               // unknown stage
        send("{\"funnelId\":\"abc\",\"flow\":\"signup\",\"events\":[" + ev("OPEN", null) + "]}")
                .andExpect(status().isBadRequest());
        send(body("other", ev("OPEN", null))).andExpect(status().isBadRequest());
        verify(repository, never()).insertAll(anyList());
    }

    @Test
    void should_answer400_when_theBatchIsEmptyOrHasMoreThan20() throws Exception {
        send(body("signup")).andExpect(status().isBadRequest());
        String[] twentyOne = IntStream.range(0, 21).mapToObj(i -> ev("OPEN", null)).toArray(String[]::new);
        send(body("signup", twentyOne)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        String[] twenty = IntStream.range(0, 20).mapToObj(i -> ev("OPEN", null)).toArray(String[]::new);
        send(body("signup", twenty)).andExpect(status().isAccepted());
    }

    @Test
    void should_answer415_when_theContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(body("signup", ev("OPEN", null))))
                .andExpect(status().isUnsupportedMediaType());
        verify(repository, never()).insertAll(anyList());
    }

    @Test
    void should_answer429WithRetryAfter_onThe121stRequestOfTheSameIp() throws Exception {
        for (int i = 0; i < 120; i++) send(body("signup", ev("OPEN", null))).andExpect(status().isAccepted());

        send(body("signup", ev("OPEN", null)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("FUNNEL_EVENTS_RATE_LIMITED"));
    }

    @Test
    void should_countTheLimitPerIp_usingTheFirstForwardedValue() throws Exception {
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "1.1.1.1")
                    .content(body("signup", ev("OPEN", null)))).andExpect(status().isAccepted());
        }
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "2.2.2.2")
                .content(body("signup", ev("OPEN", null)))).andExpect(status().isAccepted());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "1.1.1.1, 9.9.9.9")
                .content(body("signup", ev("OPEN", null)))).andExpect(status().isTooManyRequests());
    }

    @Test
    void should_answer429_when_aFunnelExceeds200EventsInADay() throws Exception {
        String[] twenty = IntStream.range(0, 20).mapToObj(i -> ev("OPEN", null)).toArray(String[]::new);
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "5.5.5." + i)
                    .content(body("signup", twenty))).andExpect(status().isAccepted());
        }

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "6.6.6.6")
                        .content(body("signup", ev("OPEN", null))))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("FUNNEL_EVENTS_RATE_LIMITED"));
    }

    @Test
    void should_answer202AndDrop_when_theCollectionIsOff() throws Exception {
        build(false);

        send(body("signup", ev("OPEN", null))).andExpect(status().isAccepted());

        verify(repository, never()).insertAll(anyList());
    }

    /** Flattens every readable field of an event so a leak of IP or user agent would show up. */
    private static final class FunnelControllerDump {
        static String of(FunnelEvent e) {
            return String.join(",", String.valueOf(e.getFunnelId()), String.valueOf(e.getFlow()),
                    String.valueOf(e.getStage()), String.valueOf(e.getStep()), String.valueOf(e.getValue()),
                    String.valueOf(e.getViewport()), String.valueOf(e.getOrganizationId()),
                    String.valueOf(e.getUserId()));
        }
    }
}

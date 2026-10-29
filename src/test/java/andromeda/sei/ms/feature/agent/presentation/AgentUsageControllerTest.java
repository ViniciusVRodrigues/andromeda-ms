package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usage.IngestAgentUsageUseCase;
import andromeda.sei.ms.feature.agent.application.usage.ListAgentTasksUseCase;
import andromeda.sei.ms.feature.agent.application.usage.ReconcileAgentUsageUseCase;
import andromeda.sei.ms.feature.agent.application.usage.UsageBatchInput;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.Allocation;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.RowResult;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.Status;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentUsageControllerTest {

    static final AgentPrincipal PRINCIPAL = new AgentPrincipal("org-1", "user-1", "dev-1");
    static final String BATCH = "8a3e0000-0000-4000-8000-000000000001";

    IngestAgentUsageUseCase ingest;
    ReconcileAgentUsageUseCase reconcile;
    ListAgentTasksUseCase tasks;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        ingest = mock(IngestAgentUsageUseCase.class);
        reconcile = mock(ReconcileAgentUsageUseCase.class);
        tasks = mock(ListAgentTasksUseCase.class);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(PRINCIPAL, "n"));
        mvc = build(new RateLimiter(), 3, 60);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    MockMvc build(RateLimiter limiter, int maxRows, int perMinute) {
        return MockMvcBuilders.standaloneSetup(new AgentUsageController(ingest, reconcile, tasks, limiter, maxRows, perMinute))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AgentExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    static String body(String batchId, String... rows) {
        return "{\"batchId\":" + (batchId == null ? "null" : "\"" + batchId + "\"") + ",\"agentVersion\":\"0.1.0\","
                + "\"timezone\":\"America/Sao_Paulo\",\"rows\":[" + String.join(",", rows) + "]}";
    }

    static final String ROW = "{\"key\":\"k1\",\"tool\":\"claude-code\",\"sessionHash\":\"9f2c9f2c9f2c9f2c\","
            + "\"model\":\"claude-sonnet-5-5\",\"hour\":\"2026-10-29T14:00:00Z\",\"day\":\"2026-10-29\","
            + "\"tokens\":{\"input\":100,\"output\":200,\"cacheRead\":0,\"cacheWrite\":0,\"thinking\":0},"
            + "\"interactions\":2,\"origin\":\"real\",\"repo\":\"github.com/a/b\",\"branch\":\"main\"}";

    UsageUploadResult result(RowResult... rows) {
        return new UsageUploadResult(BATCH, 1, 0, 0, List.of(rows));
    }

    @Test
    void should_answerTheRowsInTheWireFormat() throws Exception {
        when(ingest.execute(eq(PRINCIPAL), any())).thenReturn(result(
                new RowResult("k1", Status.ACCEPTED, Allocation.ALLOCATED, "wi-1", "AB#1", null, null, null)));

        mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(body(BATCH, ROW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(BATCH))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.results[0].status").value("accepted"))
                .andExpect(jsonPath("$.results[0].allocation").value("ALLOCATED"))
                .andExpect(jsonPath("$.results[0].taskRef").value("AB#1"))
                .andExpect(jsonPath("$.results[0].reason").doesNotExist());
        ArgumentCaptor<UsageBatchInput> captor = ArgumentCaptor.forClass(UsageBatchInput.class);
        verify(ingest).execute(eq(PRINCIPAL), captor.capture());
        UsageBatchInput.Row row = captor.getValue().rows().get(0);
        assertThat(row.output()).isEqualTo(200L);
        assertThat(row.interactions()).isEqualTo(2L);
        assertThat(captor.getValue().timezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void should_answerTheSameBodyTwice_when_theBatchIsReplayed() throws Exception {
        when(ingest.execute(eq(PRINCIPAL), any())).thenReturn(result(
                new RowResult("k1", Status.ACCEPTED, Allocation.QUEUED, null, null, null, null, null)));
        String first = mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(body(BATCH, ROW)))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(body(BATCH, ROW)))
                .andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void should_answer413WithMaxRows_when_theBatchHasMoreRowsThanTheLimit() throws Exception {
        mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON)
                        .content(body(BATCH, ROW, ROW, ROW, ROW)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.maxRows").value(3));
        verify(ingest, never()).execute(any(), any());
    }

    @Test
    void should_answer413_when_theBodyIsOverOneMebibyte() throws Exception {
        String big = "{\"batchId\":\"" + BATCH + "\",\"rows\":[],\"pad\":\"" + "x".repeat(1024 * 1024 + 10) + "\"}";
        mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(big))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void should_answer429WithRetryAfter_when_theDeviceExceedsTheUploadsPerMinute() throws Exception {
        when(ingest.execute(any(), any())).thenReturn(result());
        MockMvc limited = build(new RateLimiter(), 3, 2);
        for (int i = 0; i < 2; i++) {
            limited.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(body(BATCH, ROW)))
                    .andExpect(status().isOk());
        }
        limited.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(body(BATCH, ROW)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void should_answer400_when_theEnvelopeIsBroken() throws Exception {
        for (String bad : new String[]{"not json", "[]", body(null, ROW), body("not-a-uuid", ROW),
                "{\"batchId\":\"" + BATCH + "\"}"}) {
            mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGENT_BATCH_INVALID"));
        }
        verify(ingest, never()).execute(any(), any());
    }

    @Test
    void should_markOnlyTheRowWithTheWrongType_asMalformed() throws Exception {
        when(ingest.execute(eq(PRINCIPAL), any())).thenReturn(result());
        String wrong = ROW.replace("\"input\":100", "\"input\":\"cem\"");

        mvc.perform(post("/agent/v1/usage").contentType(MediaType.APPLICATION_JSON).content(body(BATCH, wrong, ROW)))
                .andExpect(status().isOk());

        ArgumentCaptor<UsageBatchInput> captor = ArgumentCaptor.forClass(UsageBatchInput.class);
        verify(ingest).execute(eq(PRINCIPAL), captor.capture());
        assertThat(captor.getValue().rows().get(0).malformed()).containsExactly("tokens.input");
        assertThat(captor.getValue().rows().get(1).malformed()).isEmpty();
    }

    @Test
    void should_answerTheReconciliationOfTheDevice() throws Exception {
        when(reconcile.reconcile(eq(PRINCIPAL), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 2))))
                .thenReturn(new ReconcileAgentUsageUseCase.Reconciliation("dev-1", List.of(
                        new ReconcileAgentUsageUseCase.DayTotals(LocalDate.of(2026, 10, 1), 2,
                                new ReconcileAgentUsageUseCase.Tokens(1, 2, 3, 4, 5), "abc"))));

        mvc.perform(get("/agent/v1/usage/reconcile").param("from", "2026-10-01").param("to", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("dev-1"))
                .andExpect(jsonPath("$.days[0].rows").value(2))
                .andExpect(jsonPath("$.days[0].tokens.cacheWrite").value(4))
                .andExpect(jsonPath("$.days[0].keysDigest").value("abc"));
    }

    @Test
    void should_listTheKeysOfADay() throws Exception {
        when(reconcile.keys(eq(PRINCIPAL), eq(LocalDate.of(2026, 10, 1))))
                .thenReturn(new ReconcileAgentUsageUseCase.DayKeys(LocalDate.of(2026, 10, 1), List.of("a", "b")));

        mvc.perform(get("/agent/v1/usage/keys").param("day", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[1]").value("b"));
    }

    @Test
    void should_listTheTasksOfTheUser() throws Exception {
        when(tasks.execute("org-1", "user-1")).thenReturn(List.of(
                new ListAgentTasksUseCase.TaskItem("wi-1", "AB#123", "123", "Fix", "Task", "Sprint 1")));

        mvc.perform(get("/agent/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ref").value("AB#123"))
                .andExpect(jsonPath("$.items[0].sprintName").value("Sprint 1"));
    }
}

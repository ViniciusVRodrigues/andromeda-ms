package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usage.IngestAgentUsageUseCase;
import andromeda.sei.ms.feature.agent.application.usage.ListAgentTasksUseCase;
import andromeda.sei.ms.feature.agent.application.usage.ReconcileAgentUsageUseCase;
import andromeda.sei.ms.feature.agent.application.usage.UsageBatchInput;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult;
import andromeda.sei.ms.feature.agent.domain.exception.AgentBatchInvalidException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentBatchTooLargeException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRateLimitedException;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire;
import andromeda.sei.ms.shared.infrastructure.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Usage upload v2, reconciliation and tasks of the local agent (agente-endpoint-v2, design 4, 8 and 9),
 * authenticated by the agent token. The body is read as bytes and parsed here so that a value of the
 * wrong JSON type rejects only its row, while a broken envelope rejects the whole batch.
 */
@Tag(name = "Agent")
@RestController
@RequestMapping("/agent/v1")
public class AgentUsageController {

    static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IngestAgentUsageUseCase ingest;
    private final ReconcileAgentUsageUseCase reconcile;
    private final ListAgentTasksUseCase tasks;
    private final RateLimiter rateLimiter;
    private final int maxRows;
    private final int uploadsPerMinute;

    @Autowired
    public AgentUsageController(IngestAgentUsageUseCase ingest, ReconcileAgentUsageUseCase reconcile,
                                ListAgentTasksUseCase tasks, RateLimiter rateLimiter,
                                @Value("${andromeda.agent.max-rows-per-batch:500}") int maxRows,
                                @Value("${andromeda.agent.uploads-per-minute:60}") int uploadsPerMinute) {
        this.ingest = ingest;
        this.reconcile = reconcile;
        this.tasks = tasks;
        this.rateLimiter = rateLimiter;
        this.maxRows = maxRows;
        this.uploadsPerMinute = uploadsPerMinute;
    }

    @PostMapping("/usage")
    @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
    public AgentWire.UsageUploadResponse upload(@AuthenticationPrincipal AgentPrincipal principal,
                                                @RequestBody byte[] body) {
        String limitKey = "agent-usage:" + principal.deviceId();
        if (!rateLimiter.tryAcquire(limitKey, uploadsPerMinute, WINDOW)) {
            throw new AgentRateLimitedException(rateLimiter.retryAfterSeconds(limitKey, WINDOW));
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new AgentBatchTooLargeException(maxRows);
        }
        return toResponse(ingest.execute(principal, parse(body)));
    }

    @GetMapping("/usage/reconcile")
    @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
    public AgentWire.ReconcileResponse reconcile(
            @AuthenticationPrincipal AgentPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReconcileAgentUsageUseCase.Reconciliation r = reconcile.reconcile(principal, from, to);
        return new AgentWire.ReconcileResponse(r.deviceId(), r.days().stream()
                .map(d -> new AgentWire.ReconcileDay(d.day(), d.rows(),
                        new AgentWire.ReconcileTokens(d.tokens().input(), d.tokens().output(),
                                d.tokens().cacheRead(), d.tokens().cacheWrite(), d.tokens().thinking()),
                        d.keysDigest()))
                .toList());
    }

    @GetMapping("/usage/keys")
    @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
    public AgentWire.KeysResponse keys(
            @AuthenticationPrincipal AgentPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        ReconcileAgentUsageUseCase.DayKeys k = reconcile.keys(principal, day);
        return new AgentWire.KeysResponse(k.day(), k.keys());
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('AGENT_TASKS_READ')")
    public AgentWire.TasksResponse tasks(@AuthenticationPrincipal AgentPrincipal principal) {
        return new AgentWire.TasksResponse(tasks.execute(principal.organizationId(), principal.userId()).stream()
                .map(t -> new AgentWire.TaskResponse(t.workItemId(), t.ref(), t.externalId(), t.title(), t.type(),
                        t.sprintName()))
                .toList());
    }

    // ---- body -------------------------------------------------------------------------------

    private UsageBatchInput parse(byte[] body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JacksonException e) {
            throw new AgentBatchInvalidException("JSON inválido");
        }
        if (root == null || !root.isObject()) throw new AgentBatchInvalidException("o corpo deve ser um objeto");
        JsonNode batchIdNode = root.get("batchId");
        if (batchIdNode == null || !batchIdNode.isTextual()) throw new AgentBatchInvalidException("batchId ausente");
        String batchId = batchIdNode.asText();
        try {
            UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            throw new AgentBatchInvalidException("batchId deve ser um UUID");
        }
        JsonNode rowsNode = root.get("rows");
        if (rowsNode == null || !rowsNode.isArray()) throw new AgentBatchInvalidException("rows ausente");
        if (rowsNode.size() > maxRows) throw new AgentBatchTooLargeException(maxRows);

        List<UsageBatchInput.Row> rows = new ArrayList<>();
        for (JsonNode node : rowsNode) {
            rows.add(parseRow(node));
        }
        Set<String> ignored = new HashSet<>();
        return new UsageBatchInput(batchId, text(root, "agentVersion", ignored), text(root, "timezone", ignored), rows);
    }

    private static UsageBatchInput.Row parseRow(JsonNode n) {
        Set<String> bad = new HashSet<>();
        if (n == null || !n.isObject()) {
            bad.add("row");
            return new UsageBatchInput.Row(null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, bad);
        }
        JsonNode tokens = n.get("tokens");
        if (tokens != null && !tokens.isNull() && !tokens.isObject()) {
            bad.add("tokens");
            tokens = null;
        }
        return new UsageBatchInput.Row(
                text(n, "key", bad), text(n, "tool", bad), text(n, "sessionHash", bad), text(n, "model", bad),
                text(n, "hour", bad), text(n, "day", bad),
                number(tokens, "input", "tokens.input", bad), number(tokens, "output", "tokens.output", bad),
                number(tokens, "cacheRead", "tokens.cacheRead", bad),
                number(tokens, "cacheWrite", "tokens.cacheWrite", bad),
                number(tokens, "thinking", "tokens.thinking", bad),
                number(n, "interactions", "interactions", bad),
                text(n, "origin", bad), text(n, "repo", bad), text(n, "branch", bad), text(n, "taskRef", bad),
                text(n, "attributionMethod", bad), bool(n, "branchApproximate", bad), bad);
    }

    private static String text(JsonNode parent, String field, Set<String> bad) {
        JsonNode v = parent == null ? null : parent.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) {
            bad.add(field);
            return null;
        }
        return v.asText();
    }

    private static Boolean bool(JsonNode parent, String field, Set<String> bad) {
        JsonNode v = parent == null ? null : parent.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isBoolean()) {
            bad.add(field);
            return null;
        }
        return v.booleanValue();
    }

    private static Long number(JsonNode parent, String field, String label, Set<String> bad) {
        JsonNode v = parent == null ? null : parent.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isIntegralNumber() || !v.canConvertToLong()) {
            bad.add(label);
            return null;
        }
        return v.longValue();
    }

    private static AgentWire.UsageUploadResponse toResponse(UsageUploadResult r) {
        return new AgentWire.UsageUploadResponse(r.batchId(), r.accepted(), r.duplicate(), r.rejected(),
                r.results().stream().map(x -> new AgentWire.UsageRowResponse(x.key(),
                        x.status().name().toLowerCase(),
                        x.allocation() == null ? null : x.allocation().name(),
                        x.workItemId(), x.taskRef(), x.reason(), x.field(), x.retryable())).toList());
    }
}

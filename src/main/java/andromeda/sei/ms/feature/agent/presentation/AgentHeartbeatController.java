package andromeda.sei.ms.feature.agent.presentation;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatOutput;
import andromeda.sei.ms.feature.agent.application.usecase.RecordHeartbeatUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.HeartbeatRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated by the agent token (Bearer); never touches {@code lastUploadAt}. */
@Tag(name = "Agent")
@RestController
@RequestMapping("/agent/v1")
public class AgentHeartbeatController {

    private final RecordHeartbeatUseCase recordHeartbeat;

    public AgentHeartbeatController(RecordHeartbeatUseCase recordHeartbeat) {
        this.recordHeartbeat = recordHeartbeat;
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
    public HeartbeatOutput heartbeat(@AuthenticationPrincipal AgentPrincipal principal,
                                     @Valid @RequestBody(required = false) HeartbeatRequest body) {
        HeartbeatInput in = body == null ? new HeartbeatInput(null, null, null, null)
                : new HeartbeatInput(body.agentVersion(), body.timezone(), body.pendingRows(), body.syncServed(),
                conflictUpdate(body.taskConflict()));
        return recordHeartbeat.execute(principal, in);
    }

    /** Absent field = keep (null here); JSON null = clear; object = store (a malformed object is ignored). */
    private static HeartbeatInput.ConflictUpdate conflictUpdate(JsonNode node) {
        if (node == null) return null;
        if (node.isNull()) return HeartbeatInput.ConflictUpdate.clear();
        if (!node.isObject()) return null;
        Instant at = parseInstant(text(node, "at"));
        return at == null ? null : HeartbeatInput.ConflictUpdate.set(new AgentDevice.TaskConflict(
                text(node, "taskRef"), text(node, "branch"), text(node, "branchRef"), text(node, "repo"), at));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) return null;
        String t = v.asText();
        return t.isBlank() ? null : t.length() > 300 ? t.substring(0, 300) : t;
    }

    private static Instant parseInstant(String text) {
        if (text == null) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

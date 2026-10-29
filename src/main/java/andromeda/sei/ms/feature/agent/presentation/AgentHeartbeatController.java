package andromeda.sei.ms.feature.agent.presentation;

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
        HeartbeatInput in = body == null ? new HeartbeatInput(null, null, null)
                : new HeartbeatInput(body.agentVersion(), body.timezone(), body.pendingRows());
        return recordHeartbeat.execute(principal, in);
    }
}

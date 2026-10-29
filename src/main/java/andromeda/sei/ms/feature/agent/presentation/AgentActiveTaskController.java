package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usage.ClearActiveTaskUseCase;
import andromeda.sei.ms.feature.agent.application.usage.SetActiveTaskUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.ActiveTaskRequest;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.ActiveTaskResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code andromeda task X} and {@code andromeda task --clear} (agente-endpoint-v2, design 7), by the agent token. */
@Tag(name = "Agent")
@RestController
@RequestMapping("/agent/v1/active-task")
public class AgentActiveTaskController {

    private final SetActiveTaskUseCase setActiveTask;
    private final ClearActiveTaskUseCase clearActiveTask;

    public AgentActiveTaskController(SetActiveTaskUseCase setActiveTask, ClearActiveTaskUseCase clearActiveTask) {
        this.setActiveTask = setActiveTask;
        this.clearActiveTask = clearActiveTask;
    }

    @PutMapping
    @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
    public ActiveTaskResponse set(@AuthenticationPrincipal AgentPrincipal principal,
                                  @Valid @RequestBody ActiveTaskRequest body) {
        SetActiveTaskUseCase.Output out = setActiveTask.execute(principal,
                new SetActiveTaskUseCase.Input(body.taskRef(), body.since(), body.day(), body.repo()));
        return new ActiveTaskResponse(out.workItemId(), out.ref(), out.title(), out.retroactiveRows());
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AgentPrincipal principal) {
        clearActiveTask.execute(principal);
    }
}

package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usecase.GetAgentWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Branch patterns of the organization for the agent; authenticated by the agent token, organization from the principal. */
@Tag(name = "Agent")
@RestController
@RequestMapping("/agent/v1")
public class AgentWorkItemPatternsController {

    private final GetAgentWorkItemPatternsUseCase getPatterns;

    public AgentWorkItemPatternsController(GetAgentWorkItemPatternsUseCase getPatterns) {
        this.getPatterns = getPatterns;
    }

    @GetMapping("/work-item-patterns")
    @PreAuthorize("hasAuthority('AGENT_TASKS_READ')")
    public GetAgentWorkItemPatternsUseCase.Output get(@AuthenticationPrincipal AgentPrincipal principal) {
        return getPatterns.execute(principal);
    }
}

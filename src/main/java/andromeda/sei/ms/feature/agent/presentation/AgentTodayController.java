package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usage.GetAgentTodayUseCase;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the person's agent is tracking right now (agente-endpoint-v2, design 13.2); only their own data. */
@Tag(name = "Agent devices")
@RestController
@RequestMapping("/api/v1/agent")
@PreAuthorize("hasAuthority('CONNECTOR_TOKEN_MANAGE_OWN')")
public class AgentTodayController {

    private final GetAgentTodayUseCase getToday;

    public AgentTodayController(GetAgentTodayUseCase getToday) {
        this.getToday = getToday;
    }

    @GetMapping("/today")
    public GetAgentTodayUseCase.Today today(@AuthenticationPrincipal AndromedaUserDetails user) {
        return getToday.execute(user.getOrganizationId(), user.getUserId());
    }
}

package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usage.ListAgentAllocationRowsUseCase;
import andromeda.sei.ms.feature.agent.application.usage.MoveAgentUsageUseCase;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.MoveUsageRequest;
import andromeda.sei.ms.feature.agent.presentation.dto.AgentWire.AllocationRowResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fixes the task of agent usage by hand (agente-endpoint-v2, design 15); only the person's own allocation. */
@Tag(name = "AI Usage Allocations")
@RestController
@RequestMapping("/api/v1/ai/allocations")
public class AgentAllocationMoveController {

    private final MoveAgentUsageUseCase move;
    private final ListAgentAllocationRowsUseCase listRows;

    public AgentAllocationMoveController(MoveAgentUsageUseCase move, ListAgentAllocationRowsUseCase listRows) {
        this.move = move;
        this.listRows = listRows;
    }

    @PostMapping("/{allocationId}/move")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public MoveAgentUsageUseCase.MoveResult move(@AuthenticationPrincipal AndromedaUserDetails user,
                                                 @PathVariable String allocationId,
                                                 @Valid @RequestBody MoveUsageRequest body) {
        return move.execute(user.getOrganizationId(), user.getUserId(), allocationId, body.aiUsageIds(),
                body.workItemId());
    }

    @GetMapping("/{allocationId}/rows")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public List<AllocationRowResponse> rows(@AuthenticationPrincipal AndromedaUserDetails user,
                                            @PathVariable String allocationId) {
        return listRows.execute(user.getOrganizationId(), user.getUserId(), allocationId).stream()
                .map(u -> new AllocationRowResponse(u.getId(), u.getBucketStart(), u.getTool(), u.getModel(),
                        u.getRepository(), u.getBranch(), u.isBranchApproximate(), u.getOutputTokens()))
                .toList();
    }
}

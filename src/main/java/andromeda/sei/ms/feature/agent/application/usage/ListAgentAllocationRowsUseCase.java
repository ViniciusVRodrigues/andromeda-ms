package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotFoundException;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The usage rows of an AGENT allocation, so the person can pick the ones to move (agente-telas). Only the
 * person's own, active AGENT allocation of the organization; anything else answers 404, like the move.
 */
@Component
public class ListAgentAllocationRowsUseCase {

    private final AiUsageAllocationRepository allocations;
    private final AgentUsageQueries usageQueries;

    public ListAgentAllocationRowsUseCase(AiUsageAllocationRepository allocations, AgentUsageQueries usageQueries) {
        this.allocations = allocations;
        this.usageQueries = usageQueries;
    }

    public List<AIUsage> execute(String organizationId, String userId, String allocationId) {
        AiUsageAllocation allocation = allocations.findById(allocationId, organizationId)
                .filter(a -> userId.equals(a.getUserId()))
                .filter(AiUsageAllocation::isAgent)
                .filter(AiUsageAllocation::isActive)
                .orElseThrow(AiAllocationNotFoundException::new);
        return usageQueries.findActiveRowsOfAllocation(organizationId, userId, allocation.getId());
    }
}

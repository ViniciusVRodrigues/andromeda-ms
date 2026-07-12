package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalculateTokensPerSpUseCase {

    private final WorkItemRepository workItemRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;

    public void execute(String orgId, String projectId) {
        List<WorkItem> workItems = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId);

        for (WorkItem workItem : workItems) {
            if (workItem.getEffort() == null || workItem.getEffort() == 0) {
                continue;
            }

            // Find all validated allocations that reference this work item in their splits
            List<AiUsageAllocation> allocations = allocationRepository.findByWorkItemId(workItem.getId());

            if (allocations.isEmpty()) {
                workItemRepository.updateTokensPerSp(workItem.getId(), null);
                continue;
            }

            // Collect all aiUsageIds referenced across all allocations for this work item
            List<String> allUsageIds = allocations.stream()
                    .filter(a -> a.getAiUsageIds() != null)
                    .flatMap(a -> a.getAiUsageIds().stream())
                    .distinct()
                    .collect(Collectors.toList());

            // Fetch all referenced usage records in one query
            Map<String, AIUsage> usageById = aiUsageRepository.findByIds(allUsageIds).stream()
                    .collect(Collectors.toMap(AIUsage::getId, u -> u, (a, b) -> a));

            double totalAttributedTokens = 0.0;
            for (AiUsageAllocation allocation : allocations) {
                if (allocation.getAiUsageIds() == null || allocation.getSplits() == null) continue;

                // Find the split percentage for this work item in this allocation
                double splitPercentage = allocation.getSplits().stream()
                        .filter(s -> workItem.getId().equals(s.getWorkItemId()))
                        .findFirst()
                        .map(AllocationSplit::getPercentage)
                        .map(java.math.BigDecimal::doubleValue)
                        .orElse(0.0);

                if (splitPercentage == 0.0) continue;

                // Sum tokens from all usage records referenced by this allocation
                double allocationTokens = allocation.getAiUsageIds().stream()
                        .map(usageById::get)
                        .filter(u -> u != null)
                        .mapToDouble(u -> u.getInputTokens() + u.getOutputTokens())
                        .sum();

                totalAttributedTokens += allocationTokens * splitPercentage;
            }

            double tokensPerSp = totalAttributedTokens / workItem.getEffort();
            workItemRepository.updateTokensPerSp(workItem.getId(), tokensPerSp > 0 ? tokensPerSp : null);
        }
    }
}

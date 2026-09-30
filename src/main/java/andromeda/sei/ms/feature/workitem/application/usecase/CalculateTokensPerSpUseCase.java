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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tokens per unit of effort of each work item: tokens of the usage records its validated
 * allocations cover, weighted by the split percentage, divided by the item's effort.
 *
 * <p>metrics-correctness 11.5: allocations and usage records are read once for the whole project,
 * tenant-scoped (organization + active, {@code VALIDATED} only) — before, one unscoped allocation
 * query per work item plus one usage query per item.
 */
@Service
@RequiredArgsConstructor
public class CalculateTokensPerSpUseCase {

    private final WorkItemRepository workItemRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;

    public void execute(String orgId, String projectId) {
        List<WorkItem> withEffort = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId).stream()
                .filter(WorkItem::isActive)
                .filter(wi -> wi.getId() != null && wi.getEffort() != null && wi.getEffort() != 0)
                .toList();
        if (withEffort.isEmpty()) return;

        Set<String> workItemIds = withEffort.stream().map(WorkItem::getId).collect(Collectors.toSet());
        List<AiUsageAllocation> allocations =
                allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn(orgId, workItemIds);
        Map<String, AIUsage> usageById = usageById(orgId, allocations);

        for (WorkItem workItem : withEffort) {
            double tokens = attributedTokens(workItem.getId(), allocations, usageById);
            double tokensPerSp = tokens / workItem.getEffort();
            workItemRepository.updateTokensPerSp(workItem.getId(), tokensPerSp > 0 ? tokensPerSp : null);
        }
    }

    private Map<String, AIUsage> usageById(String orgId, List<AiUsageAllocation> allocations) {
        Set<String> usageIds = allocations.stream()
                .filter(a -> a.getAiUsageIds() != null)
                .flatMap(a -> a.getAiUsageIds().stream())
                .collect(Collectors.toSet());
        return aiUsageRepository.findByOrganizationIdAndIdIn(orgId, usageIds).stream()
                .collect(Collectors.toMap(AIUsage::getId, Function.identity(), (a, b) -> a));
    }

    private double attributedTokens(String workItemId, List<AiUsageAllocation> allocations,
                                    Map<String, AIUsage> usageById) {
        double total = 0.0;
        for (AiUsageAllocation allocation : allocations) {
            if (allocation.getAiUsageIds() == null || allocation.getSplits() == null) continue;
            double share = allocation.getSplits().stream()
                    .filter(s -> workItemId.equals(s.getWorkItemId()) && s.getPercentage() != null)
                    .map(AllocationSplit::getPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .doubleValue();
            if (share == 0.0) continue;
            double tokens = allocation.getAiUsageIds().stream()
                    .map(usageById::get)
                    .filter(Objects::nonNull)
                    .mapToDouble(u -> u.getInputTokens() + u.getOutputTokens())
                    .sum();
            total += tokens * share;
        }
        return total;
    }
}

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

import java.time.LocalDate;
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
            if (workItem.getStoryPoints() == null || workItem.getStoryPoints() == 0) {
                continue;
            }

            List<AiUsageAllocation> allocations = allocationRepository.findByWorkItemId(workItem.getId());

            if (allocations.isEmpty()) {
                workItemRepository.updateTokensPerSp(workItem.getId(), null);
                continue;
            }

            Map<String, List<LocalDate>> daysByUserId = allocations.stream()
                    .collect(Collectors.groupingBy(
                            AiUsageAllocation::getUserId,
                            Collectors.mapping(AiUsageAllocation::getDay, Collectors.toList())
                    ));

            Map<String, Map<LocalDate, AIUsage>> usageByUserAndDay = daysByUserId.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> {
                                List<AIUsage> usages = aiUsageRepository.findByOrganizationIdAndUserIdAndDayIn(
                                        orgId, e.getKey(), e.getValue());
                                return usages.stream()
                                        .collect(Collectors.toMap(AIUsage::getDay, u -> u, (a, b) -> a));
                            }
                    ));

            double totalAttributedTokens = 0.0;
            for (AiUsageAllocation allocation : allocations) {
                Map<LocalDate, AIUsage> dayMap = usageByUserAndDay.get(allocation.getUserId());
                if (dayMap == null) continue;
                AIUsage usage = dayMap.get(allocation.getDay());
                if (usage == null) continue;

                double splitPercentage = allocation.getSplits() == null ? 0.0 :
                        allocation.getSplits().stream()
                                .filter(s -> workItem.getId().equals(s.getWorkItemId()))
                                .findFirst()
                                .map(AllocationSplit::getPercentage)
                                .map(java.math.BigDecimal::doubleValue)
                                .orElse(0.0);

                double totalTokens = usage.getInputTokens() + usage.getOutputTokens();
                totalAttributedTokens += totalTokens * splitPercentage;
            }

            double tokensPerSp = totalAttributedTokens / workItem.getStoryPoints();
            workItemRepository.updateTokensPerSp(workItem.getId(), tokensPerSp);
        }
    }
}

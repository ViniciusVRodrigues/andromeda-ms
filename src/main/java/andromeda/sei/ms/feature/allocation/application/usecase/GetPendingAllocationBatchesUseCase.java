package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.dto.PendingBatchOutput;
import andromeda.sei.ms.feature.allocation.application.dto.SplitOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPendingAllocationBatchesUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;

    public List<PendingBatchOutput> execute(String organizationId, String userId) {
        // Primary source: days that have a PENDING_VALIDATION allocation
        List<AiUsageAllocation> pendingAllocations = allocationRepository
                .findByOrgUserAndStatus(organizationId, userId, AiUsageAllocation.Status.PENDING_VALIDATION);

        Map<LocalDate, AiUsageAllocation> pendingByDay = pendingAllocations.stream()
                .collect(Collectors.toMap(AiUsageAllocation::getDay, a -> a, (a, b) -> a));

        // Secondary source: AIUsage records with allocationId == null (no auto-suggestion possible)
        List<AIUsage> nullAllocatedUsage = aiUsageRepository
                .findUnallocatedByOrganizationIdAndUserId(organizationId, userId);

        Map<LocalDate, List<AIUsage>> unallocatedByDay = nullAllocatedUsage.stream()
                .collect(Collectors.groupingBy(AIUsage::getDay));

        // Merge: collect all days from both sources
        Map<LocalDate, List<AIUsage>> usageByDay = new HashMap<>();
        for (LocalDate day : pendingByDay.keySet()) {
            // For days with a PENDING_VALIDATION allocation, fetch the usage records it covers
            AiUsageAllocation pending = pendingByDay.get(day);
            List<String> coveredIds = pending.getAiUsageIds() != null ? pending.getAiUsageIds() : List.of();
            if (!coveredIds.isEmpty()) {
                List<AIUsage> coveredUsage = aiUsageRepository
                        .findByOrganizationIdAndUserIdAndDayIn(organizationId, userId, List.of(day));
                usageByDay.put(day, coveredUsage);
            } else {
                usageByDay.put(day, List.of());
            }
        }
        // Add days from null-allocationId fallback that don't already have a PENDING allocation
        for (Map.Entry<LocalDate, List<AIUsage>> entry : unallocatedByDay.entrySet()) {
            if (!pendingByDay.containsKey(entry.getKey())) {
                usageByDay.put(entry.getKey(), entry.getValue());
            }
        }

        return usageByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> buildBatch(organizationId, userId, entry.getKey(), entry.getValue(),
                        pendingByDay.get(entry.getKey())))
                .toList();
    }

    private PendingBatchOutput buildBatch(String organizationId, String userId,
                                          LocalDate day, List<AIUsage> records,
                                          AiUsageAllocation pendingAllocation) {
        long totalInputTokens = records.stream().mapToLong(AIUsage::getInputTokens).sum();
        long totalOutputTokens = records.stream().mapToLong(AIUsage::getOutputTokens).sum();
        long totalCacheReadTokens = records.stream().mapToLong(AIUsage::getCacheReadTokens).sum();
        long totalCacheWriteTokens = records.stream().mapToLong(AIUsage::getCacheWriteTokens).sum();
        int totalInteractions = records.stream().mapToInt(AIUsage::getInteractions).sum();
        BigDecimal estimatedCostUsd = records.stream()
                .map(r -> r.getCostUsd() != null ? r.getCostUsd() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String suggestionAllocationId = null;
        List<SplitOutput> suggestion = List.of();

        if (pendingAllocation != null) {
            suggestionAllocationId = pendingAllocation.getId();
            suggestion = pendingAllocation.getSplits() != null
                    ? pendingAllocation.getSplits().stream()
                            .map(s -> new SplitOutput(s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage()))
                            .toList()
                    : List.of();
        }

        return new PendingBatchOutput(
                day, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalInteractions,
                estimatedCostUsd, suggestionAllocationId, suggestion);
    }
}

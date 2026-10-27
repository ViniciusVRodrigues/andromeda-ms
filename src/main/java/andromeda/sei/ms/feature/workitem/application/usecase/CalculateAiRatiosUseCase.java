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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI ratios of each work item (complexity-effort D3): tokens of the usage records its validated
 * allocations cover, weighted by the split percentage, divided by the item's effective complexity
 * ({@code tokensPerPoint}) and by its logged effort ({@code taskEffort}, {@code tokensPerHour}).
 * A ratio is {@code null} when its divisor is null or zero, or when there are no tokens.
 *
 * <p>metrics-correctness 11.5: allocations and usage records are read once for the whole project,
 * tenant-scoped (organization + active, {@code VALIDATED} only) — before, one unscoped allocation
 * query per work item plus one usage query per item.
 */
@Service
@RequiredArgsConstructor
public class CalculateAiRatiosUseCase {

    private final WorkItemRepository workItemRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final EffortBearingLevelResolver effortResolver;

    public void execute(String orgId, String projectId) {
        List<WorkItem> active = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId).stream()
                .filter(WorkItem::isActive)
                .toList();
        EffortBearingLevelResolver.EffortScope scope = effortResolver.scope(active);

        List<Divisors> measurable = new ArrayList<>();
        for (WorkItem item : active) {
            if (item.getId() == null) continue;
            Double points = positiveOrNull(scope.effectiveComplexity(item));
            Double hours = positiveOrNull(scope.taskEffort(item));
            if (points != null || hours != null) measurable.add(new Divisors(item, points, hours));
        }
        if (measurable.isEmpty()) return;

        Set<String> workItemIds = measurable.stream().map(d -> d.item().getId()).collect(Collectors.toSet());
        List<AiUsageAllocation> allocations =
                allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn(orgId, workItemIds);
        Map<String, AIUsage> usageById = usageById(orgId, allocations);

        for (Divisors d : measurable) {
            double tokens = attributedTokens(d.item().getId(), allocations, usageById);
            workItemRepository.updateAiRatios(d.item().getId(), ratio(tokens, d.points()), ratio(tokens, d.hours()));
        }
    }

    private record Divisors(WorkItem item, Double points, Double hours) {}

    private static Double positiveOrNull(Double value) {
        return value == null || value <= 0 ? null : value;
    }

    private static Double ratio(double tokens, Double divisor) {
        if (divisor == null) return null;
        double value = tokens / divisor;
        return value > 0 ? value : null;
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

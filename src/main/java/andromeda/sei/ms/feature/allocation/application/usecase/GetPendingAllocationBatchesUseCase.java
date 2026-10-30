package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.AgentReviewReasonCalculator;
import andromeda.sei.ms.feature.allocation.application.dto.PendingBatchOutput;
import andromeda.sei.ms.feature.allocation.application.dto.ReviewOutput;
import andromeda.sei.ms.feature.allocation.application.dto.SuggestionItemOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPendingAllocationBatchesUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final WorkItemRepository workItemRepository;

    public List<PendingBatchOutput> execute(String organizationId, String userId) {
        // Primary source: days that have a PENDING_VALIDATION allocation (inactive ones were absorbed)
        List<AiUsageAllocation> pendingAllocations = allocationRepository
                .findByOrgUserAndStatus(organizationId, userId, AiUsageAllocation.Status.PENDING_VALIDATION).stream()
                .filter(AiUsageAllocation::isActive)
                .toList();

        // One entry per day with every pending allocation of the day (the agent's and the automatic one).
        Map<LocalDate, List<AiUsageAllocation>> pendingByDay = pendingAllocations.stream()
                .collect(Collectors.groupingBy(AiUsageAllocation::getDay));

        // Secondary source: AIUsage records with allocationId == null (no auto-suggestion possible)
        List<AIUsage> nullAllocatedUsage = aiUsageRepository
                .findUnallocatedByOrganizationIdAndUserId(organizationId, userId);

        Map<LocalDate, List<AIUsage>> unallocatedByDay = nullAllocatedUsage.stream()
                .collect(Collectors.groupingBy(AIUsage::getDay));

        // Merge: collect all days from both sources
        Map<LocalDate, List<AIUsage>> usageByDay = new HashMap<>();
        for (LocalDate day : pendingByDay.keySet()) {
            // For days with a PENDING_VALIDATION allocation, fetch the usage records it covers
            boolean anyCovered = pendingByDay.get(day).stream()
                    .anyMatch(p -> p.getAiUsageIds() != null && !p.getAiUsageIds().isEmpty());
            if (anyCovered) {
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
                .map(entry -> buildBatch(organizationId, entry.getKey(), entry.getValue(),
                        pendingByDay.get(entry.getKey())))
                .toList();
    }

    private PendingBatchOutput buildBatch(String organizationId, LocalDate day, List<AIUsage> records,
                                          List<AiUsageAllocation> pendingAllocations) {
        long totalInputTokens = records.stream().mapToLong(AIUsage::getInputTokens).sum();
        long totalOutputTokens = records.stream().mapToLong(AIUsage::getOutputTokens).sum();
        long totalCacheReadTokens = records.stream().mapToLong(AIUsage::getCacheReadTokens).sum();
        long totalCacheWriteTokens = records.stream().mapToLong(AIUsage::getCacheWriteTokens).sum();
        int totalInteractions = records.stream().mapToInt(AIUsage::getInteractions).sum();
        BigDecimal estimatedCostUsd = records.stream()
                .map(r -> r.getCostUsd() != null ? r.getCostUsd() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String suggestionAllocationId = null;
        List<String> allocationIds = List.of();
        List<SuggestionItemOutput> suggestion = List.of();
        ReviewOutput review = new ReviewOutput("SUGGESTED", List.of(), List.of());

        if (pendingAllocations != null && !pendingAllocations.isEmpty()) {
            allocationIds = pendingAllocations.stream().map(AiUsageAllocation::getId).toList();
            suggestionAllocationId = allocationIds.get(0);
            suggestion = suggestionOf(organizationId, pendingAllocations, records);
            review = reviewOf(pendingAllocations);
        }

        return new PendingBatchOutput(
                day, totalInputTokens, totalOutputTokens, totalCacheReadTokens, totalCacheWriteTokens, totalInteractions,
                estimatedCostUsd, suggestionAllocationId, allocationIds, suggestion, review);
    }

    /**
     * One entry per task over all the pending allocations of the day. The share of each task is weighted by
     * the tokens (input plus output) of the rows of the allocation it comes from, sums 1.0000, and the last
     * entry takes the rounding.
     */
    private List<SuggestionItemOutput> suggestionOf(String organizationId, List<AiUsageAllocation> pending,
                                                    List<AIUsage> dayRecords) {
        Map<String, Long> tokensById = new HashMap<>();
        dayRecords.forEach(r -> tokensById.put(r.getId(), r.getInputTokens() + r.getOutputTokens()));

        Map<AiUsageAllocation, Long> tokensOf = new HashMap<>();
        long totalTokens = 0;
        for (AiUsageAllocation a : pending) {
            long t = a.getAiUsageIds() == null ? 0 : a.getAiUsageIds().stream()
                    .mapToLong(id -> tokensById.getOrDefault(id, 0L)).sum();
            tokensOf.put(a, t);
            totalTokens += t;
        }

        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        Map<String, String> titles = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        Map<String, String> overridden = new HashMap<>();
        for (AiUsageAllocation a : pending) {
            // Without tokens to weigh by, the allocations count the same.
            BigDecimal weight = totalTokens == 0 ? BigDecimal.ONE : BigDecimal.valueOf(tokensOf.get(a));
            if (a.getSplits() == null) continue;
            for (var s : a.getSplits()) {
                if (s.getWorkItemId() == null) continue;
                BigDecimal share = s.getPercentage() == null ? BigDecimal.ZERO : s.getPercentage();
                weights.merge(s.getWorkItemId(), weight.multiply(share), BigDecimal::add);
                titles.putIfAbsent(s.getWorkItemId(), s.getWorkItemTitle());
                String source = sourceOf(a);
                if (source != null) sources.putIfAbsent(s.getWorkItemId(), source);
                if (a.getAgent() != null && a.getAgent().overriddenTaskRef() != null) {
                    overridden.putIfAbsent(s.getWorkItemId(), a.getAgent().overriddenTaskRef());
                }
            }
        }

        BigDecimal sum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SuggestionItemOutput> out = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        int i = 0;
        for (Map.Entry<String, BigDecimal> e : weights.entrySet()) {
            boolean last = ++i == weights.size();
            BigDecimal pct;
            if (last) {
                pct = new BigDecimal("1.0000").subtract(given);
            } else if (sum.signum() == 0) {
                pct = BigDecimal.ZERO.setScale(4);
            } else {
                pct = e.getValue().divide(sum, 4, RoundingMode.HALF_UP);
            }
            given = given.add(pct);
            String id = e.getKey();
            String displayId = workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                    .map(AgentReviewReasonCalculator::displayId).orElse(null);
            String mark = overridden.get(id);
            out.add(new SuggestionItemOutput(id, titles.get(id), pct, displayId, sources.get(id),
                    mark == null ? null : new SuggestionItemOutput.OverriddenMark(mark)));
        }
        return out;
    }

    private static String sourceOf(AiUsageAllocation a) {
        if (a.isAgent()) {
            return a.getAgent() != null && a.getAgent().resolvedBy() != null ? a.getAgent().resolvedBy().name() : null;
        }
        return a.suggestionSourceOrDefault().name();
    }

    private static ReviewOutput reviewOf(List<AiUsageAllocation> pending) {
        Map<String, ReviewOutput.Detail> details = new LinkedHashMap<>();
        for (AiUsageAllocation a : pending) {
            if (a.getReviewReasons() == null) continue;
            for (var r : a.getReviewReasons()) {
                String key = r.code() + "|" + r.workItemId();
                ReviewOutput.Detail old = details.get(key);
                if (old == null || (r.ratio() != null && (old.ratio() == null || r.ratio() > old.ratio()))) {
                    details.put(key, new ReviewOutput.Detail(r.code().name(), r.workItemId(), r.displayId(), r.ratio()));
                }
            }
        }
        List<String> reasons = details.values().stream().map(ReviewOutput.Detail::code).distinct().toList();
        return new ReviewOutput(reasons.isEmpty() ? "SUGGESTED" : "NEEDS_REVIEW", reasons,
                new ArrayList<>(details.values()));
    }
}

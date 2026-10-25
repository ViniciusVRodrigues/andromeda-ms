package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI cost of a project (team-dashboard, design D6): for every active {@code VALIDATED} allocation
 * of the window, the cost of the usage records it covers ({@code aiUsageIds}) weighted by the sum
 * of its split percentages that point to work items of the project. This is the same basis as the
 * developer dashboard's cost per work item, so both dashboards answer the same question the same
 * way — and a member working on two projects is charged to each only for what was allocated there.
 *
 * <p>Cost not allocated yet is reported apart: usage records of the members in the window that no
 * allocation claimed ({@code allocationId == null}).
 */
@Component
@RequiredArgsConstructor
public class ProjectAiCostCalculator {

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;

    public ProjectAiCost calculate(String organizationId, Set<String> projectWorkItemIds,
                                   Collection<String> memberUserIds, LocalDate from, LocalDate to) {
        List<AiUsageAllocation> allocations = projectAllocations(organizationId, projectWorkItemIds, from, to);
        Map<String, AIUsage> usageById = allocations.isEmpty() ? Map.of() : usageById(organizationId, allocations);
        return new ProjectAiCost(
                allocatedByUser(allocations, usageById, projectWorkItemIds),
                unallocatedByUser(organizationId, memberUserIds, from, to),
                allocatedTokens(allocations, usageById, projectWorkItemIds));
    }

    /**
     * Tokens (input + output) behind the allocated cost, same allocations and same share weighting;
     * {@code null} when nothing is allocated to the project in the window.
     */
    private static Long allocatedTokens(List<AiUsageAllocation> allocations, Map<String, AIUsage> usageById,
                                        Set<String> projectWorkItemIds) {
        if (allocations.isEmpty()) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (AiUsageAllocation allocation : allocations) {
            if (allocation.getAiUsageIds() == null) continue;
            BigDecimal share = projectShare(allocation, projectWorkItemIds);
            for (String id : allocation.getAiUsageIds()) {
                AIUsage usage = usageById.get(id);
                if (usage == null) continue;
                total = total.add(share.multiply(BigDecimal.valueOf(usage.getInputTokens() + usage.getOutputTokens())));
            }
        }
        return total.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    /**
     * The usage records behind {@link ProjectAiCost#allocatedByUser}, each with the share of it that
     * the project carries: summing {@code costUsd × share} gives {@link ProjectAiCost#totalAllocated}
     * for the same window, so per-day and per-model breakdowns reconcile with the headline cost.
     */
    public List<AllocatedUsage> allocatedUsage(String organizationId, Set<String> projectWorkItemIds,
                                               LocalDate from, LocalDate to) {
        List<AiUsageAllocation> allocations = projectAllocations(organizationId, projectWorkItemIds, from, to);
        if (allocations.isEmpty()) return List.of();
        Map<String, AIUsage> usageById = usageById(organizationId, allocations);
        return allocations.stream()
                .filter(a -> a.getAiUsageIds() != null)
                .flatMap(a -> a.getAiUsageIds().stream()
                        .map(usageById::get)
                        .filter(Objects::nonNull)
                        .map(usage -> new AllocatedUsage(usage, projectShare(a, projectWorkItemIds))))
                .toList();
    }

    private List<AiUsageAllocation> projectAllocations(String organizationId, Set<String> projectWorkItemIds,
                                                       LocalDate from, LocalDate to) {
        if (projectWorkItemIds.isEmpty()) return List.of();
        // Scoped by the project's work items in the query (indexed), not the whole organization's
        // allocations of the window filtered here; the share filter below stays the source of truth.
        return allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(
                        organizationId, projectWorkItemIds, from, to).stream()
                .filter(a -> projectShare(a, projectWorkItemIds).signum() > 0)
                .toList();
    }

    private Map<String, AIUsage> usageById(String organizationId, List<AiUsageAllocation> allocations) {
        Set<String> usageIds = allocations.stream()
                .filter(a -> a.getAiUsageIds() != null)
                .flatMap(a -> a.getAiUsageIds().stream())
                .collect(Collectors.toSet());
        return aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(organizationId, usageIds).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(AIUsage::getId, u -> u, (first, second) -> first));
    }

    private static Map<String, BigDecimal> allocatedByUser(List<AiUsageAllocation> allocations,
                                                           Map<String, AIUsage> usageById,
                                                           Set<String> projectWorkItemIds) {
        if (allocations.isEmpty()) return Map.of();
        Map<String, BigDecimal> byUser = new HashMap<>();
        for (AiUsageAllocation allocation : allocations) {
            BigDecimal basis = allocation.getAiUsageIds() == null ? BigDecimal.ZERO : allocation.getAiUsageIds()
                    .stream().map(usageById::get)
                    .filter(u -> u != null && u.getId() != null && u.getCostUsd() != null)
                    .map(AIUsage::getCostUsd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            byUser.merge(allocation.getUserId(), basis.multiply(projectShare(allocation, projectWorkItemIds)),
                    BigDecimal::add);
        }
        return byUser;
    }

    private static BigDecimal projectShare(AiUsageAllocation allocation, Set<String> projectWorkItemIds) {
        if (allocation.getSplits() == null) return BigDecimal.ZERO;
        return allocation.getSplits().stream()
                .filter(split -> split.getPercentage() != null && projectWorkItemIds.contains(split.getWorkItemId()))
                .map(AllocationSplit::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> unallocatedByUser(String organizationId, Collection<String> memberUserIds,
                                                      LocalDate from, LocalDate to) {
        // Summed per (user, day) in the database: only the unclaimed cost is needed, not every record.
        return aiUsageRepository.summarizeByUserAndDay(organizationId, memberUserIds, from, to).stream()
                .filter(d -> d.userId() != null && d.unallocatedCostUsd() != null && d.unallocatedCostUsd().signum() != 0)
                .collect(Collectors.toMap(AiUsageUserDay::userId, AiUsageUserDay::unallocatedCostUsd, BigDecimal::add));
    }

    /** A usage record and the fraction of it (sum of the project's split percentages) the project carries. */
    public record AllocatedUsage(AIUsage usage, BigDecimal share) {

        /** {@code costUsd × share}; zero for a record without cost. */
        public BigDecimal allocatedCostUsd() {
            return usage.getCostUsd() == null ? BigDecimal.ZERO : usage.getCostUsd().multiply(share);
        }
    }

    /** Allocated (project) and not-yet-allocated cost per user. */
    public record ProjectAiCost(Map<String, BigDecimal> allocatedByUser, Map<String, BigDecimal> unallocatedByUser,
                                Long allocatedTokens) {

        public ProjectAiCost(Map<String, BigDecimal> allocatedByUser, Map<String, BigDecimal> unallocatedByUser) {
            this(allocatedByUser, unallocatedByUser, null);
        }

        public BigDecimal allocatedOf(String userId) {
            return allocatedByUser.getOrDefault(userId, BigDecimal.ZERO);
        }

        public BigDecimal unallocatedOf(String userId) {
            return unallocatedByUser.getOrDefault(userId, BigDecimal.ZERO);
        }

        public BigDecimal totalAllocated() {
            return allocatedByUser.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal totalUnallocated() {
            return unallocatedByUser.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}

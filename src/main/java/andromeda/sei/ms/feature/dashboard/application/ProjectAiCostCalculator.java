package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverageDetail;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * The cost and tokens behind each allocation are summed in the database (dashboard-agregado-mongo D2).
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
        List<AllocationCoverage> allocations = projectWorkItemIds.isEmpty() ? List.of()
                : allocationRepository.summarizeValidatedCoverage(organizationId, projectWorkItemIds, from, to)
                        .stream()
                        .filter(a -> projectShare(a.splits(), projectWorkItemIds).signum() > 0)
                        .toList();
        return new ProjectAiCost(
                allocatedByUser(allocations, projectWorkItemIds),
                unallocatedByUser(organizationId, memberUserIds, from, to),
                allocatedTokens(allocations, projectWorkItemIds));
    }

    /**
     * Tokens (input + output) behind the allocated cost, same allocations and same share weighting;
     * {@code null} when nothing is allocated to the project in the window.
     */
    private static Long allocatedTokens(List<AllocationCoverage> allocations, Set<String> projectWorkItemIds) {
        if (allocations.isEmpty()) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (AllocationCoverage allocation : allocations) {
            total = total.add(projectShare(allocation.splits(), projectWorkItemIds)
                    .multiply(BigDecimal.valueOf(allocation.tokens())));
        }
        return total.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    /**
     * The usage behind {@link ProjectAiCost#allocatedByUser}, per (day, model) of the usage record, each
     * with the share of it that the project carries: summing {@code costUsd × share} gives
     * {@link ProjectAiCost#totalAllocated} for the same window, so per-day and per-model breakdowns
     * reconcile with the headline cost.
     */
    public List<AllocatedUsage> allocatedUsage(String organizationId, Set<String> projectWorkItemIds,
                                               LocalDate from, LocalDate to) {
        if (projectWorkItemIds.isEmpty()) return List.of();
        List<AllocatedUsage> result = new ArrayList<>();
        for (AllocationCoverageDetail d : allocationRepository.summarizeValidatedCoverageByDayAndModel(
                organizationId, projectWorkItemIds, from, to)) {
            BigDecimal share = projectShare(d.splits(), projectWorkItemIds);
            if (share.signum() <= 0) continue;
            result.add(new AllocatedUsage(d.day(), d.model(), d.inputTokens(), d.outputTokens(), d.interactions(),
                    d.costUsd(), share));
        }
        return result;
    }

    private static Map<String, BigDecimal> allocatedByUser(List<AllocationCoverage> allocations,
                                                           Set<String> projectWorkItemIds) {
        if (allocations.isEmpty()) return Map.of();
        Map<String, BigDecimal> byUser = new HashMap<>();
        for (AllocationCoverage allocation : allocations) {
            BigDecimal basis = allocation.costUsd() == null ? BigDecimal.ZERO : allocation.costUsd();
            byUser.merge(allocation.userId(), basis.multiply(projectShare(allocation.splits(), projectWorkItemIds)),
                    BigDecimal::add);
        }
        return byUser;
    }

    private static BigDecimal projectShare(List<AllocationSplit> splits, Set<String> projectWorkItemIds) {
        if (splits == null) return BigDecimal.ZERO;
        return splits.stream()
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

    /** Usage of one (day, model) and the fraction of it (sum of the project's split percentages) the project carries. */
    public record AllocatedUsage(LocalDate day, String model, long inputTokens, long outputTokens, long interactions,
                                 BigDecimal costUsd, BigDecimal share) {

        /** {@code costUsd × share}; zero for usage without cost. */
        public BigDecimal allocatedCostUsd() {
            return costUsd == null ? BigDecimal.ZERO : costUsd.multiply(share);
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

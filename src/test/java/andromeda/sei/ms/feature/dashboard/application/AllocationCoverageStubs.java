package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverageDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Test double of the database aggregation (dashboard-agregado-mongo D2): from allocations and the usage
 * records they cite, builds the coverage rows the repository would return (a repeated id counts twice,
 * a cited record that is not in the list is not found).
 */
public final class AllocationCoverageStubs {

    private AllocationCoverageStubs() {}

    public static void stub(AiUsageAllocationRepository repository, String orgId, List<AiUsageAllocation> allocations,
                            List<AIUsage> usages) {
        lenient().when(repository.summarizeValidatedCoverage(eq(orgId), anyCollection(), any(), any()))
                .thenAnswer(inv -> coverage(inWindow(allocations, inv.getArgument(2), inv.getArgument(3)), usages));
        lenient().when(repository.summarizeValidatedCoverageByDayAndModel(eq(orgId), anyCollection(), any(), any()))
                .thenAnswer(inv -> detail(inWindow(allocations, inv.getArgument(2), inv.getArgument(3)), usages));
    }

    public static void stub(AiUsageAllocationRepository repository, String orgId, AiUsageAllocation allocation,
                            AIUsage... usages) {
        stub(repository, orgId, List.of(allocation), List.of(usages));
    }

    private static List<AiUsageAllocation> inWindow(List<AiUsageAllocation> allocations, LocalDate from, LocalDate to) {
        return allocations.stream().filter(a -> (from == null || !a.getDay().isBefore(from))
                && (to == null || !a.getDay().isAfter(to))).toList();
    }

    public static List<AllocationCoverage> coverage(List<AiUsageAllocation> allocations, List<AIUsage> usages) {
        Map<String, AIUsage> byId = byId(usages);
        List<AllocationCoverage> rows = new ArrayList<>();
        for (AiUsageAllocation a : allocations) {
            BigDecimal cost = BigDecimal.ZERO;
            long tokens = 0;
            for (AIUsage u : found(a, byId)) {
                if (u.getCostUsd() != null) cost = cost.add(u.getCostUsd());
                tokens += u.getInputTokens() + u.getOutputTokens();
            }
            rows.add(new AllocationCoverage(a.getId(), a.getUserId(), a.getDay(), a.getSplits(), cost, tokens));
        }
        return rows;
    }

    public static List<AllocationCoverageDetail> detail(List<AiUsageAllocation> allocations, List<AIUsage> usages) {
        Map<String, AIUsage> byId = byId(usages);
        List<AllocationCoverageDetail> rows = new ArrayList<>();
        for (AiUsageAllocation a : allocations) {
            Map<List<Object>, List<AIUsage>> groups = new LinkedHashMap<>();
            for (AIUsage u : found(a, byId)) {
                groups.computeIfAbsent(java.util.Arrays.asList(u.getDay(), u.getModel()), k -> new ArrayList<>()).add(u);
            }
            groups.forEach((key, list) -> rows.add(new AllocationCoverageDetail(a.getId(), a.getUserId(), a.getSplits(),
                    (LocalDate) key.get(0), (String) key.get(1),
                    list.stream().mapToLong(AIUsage::getInputTokens).sum(),
                    list.stream().mapToLong(AIUsage::getOutputTokens).sum(),
                    list.stream().mapToLong(AIUsage::getInteractions).sum(),
                    list.stream().map(AIUsage::getCostUsd).filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))));
        }
        return rows;
    }

    private static Map<String, AIUsage> byId(List<AIUsage> usages) {
        Map<String, AIUsage> byId = new LinkedHashMap<>();
        usages.stream().filter(u -> u.getId() != null).forEach(u -> byId.putIfAbsent(u.getId(), u));
        return byId;
    }

    private static List<AIUsage> found(AiUsageAllocation a, Map<String, AIUsage> byId) {
        if (a.getAiUsageIds() == null) return List.of();
        return a.getAiUsageIds().stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}

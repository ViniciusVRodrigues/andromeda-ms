package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageDayModel;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageEditorTotal;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Test double of the database sums: derives the summaries the repositories return from plain fixture lists. */
final class OrganizationAiSums {

    private OrganizationAiSums() {}

    private static List<AIUsage> of(List<AIUsage> usage, Collection<String> users, LocalDate from, LocalDate to) {
        return usage.stream().filter(AIUsage::isActive).filter(u -> users.contains(u.getUserId())
                && !u.getDay().isBefore(from) && !u.getDay().isAfter(to)).toList();
    }

    private static BigDecimal cost(Collection<AIUsage> rows) {
        return rows.stream().map(AIUsage::getCostUsd).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static List<AiUsageDayModel> byDayAndModel(List<AIUsage> usage, Collection<String> users, LocalDate from, LocalDate to) {
        Map<List<Object>, List<AIUsage>> groups = new LinkedHashMap<>();
        of(usage, users, from, to).forEach(u -> groups.computeIfAbsent(Arrays.asList(u.getDay(), u.getModel()),
                k -> new ArrayList<>()).add(u));
        return groups.values().stream().map(g -> new AiUsageDayModel(g.get(0).getDay(), g.get(0).getModel(),
                g.stream().mapToLong(AIUsage::getInputTokens).sum(), g.stream().mapToLong(AIUsage::getOutputTokens).sum(),
                g.stream().mapToLong(AIUsage::getInteractions).sum(), cost(g),
                g.stream().map(AIUsage::getUploadedAt).filter(Objects::nonNull).max(Comparator.<Instant>naturalOrder())
                        .orElse(null))).toList();
    }

    static List<AiUsageEditorTotal> byEditor(List<AIUsage> usage, Collection<String> users, LocalDate from, LocalDate to) {
        Map<String, Long> totals = new LinkedHashMap<>();
        of(usage, users, from, to).forEach(u -> totals.merge(u.getEditor(), (long) u.getInteractions(), Long::sum));
        return totals.entrySet().stream().map(e -> new AiUsageEditorTotal(e.getKey(), e.getValue())).toList();
    }

    static List<AiUsageUserDay> byUserAndDay(List<AIUsage> usage, Collection<String> users, LocalDate from, LocalDate to) {
        Map<List<Object>, List<AIUsage>> groups = new LinkedHashMap<>();
        of(usage, users, from, to).forEach(u -> groups.computeIfAbsent(Arrays.asList(u.getUserId(), u.getDay()),
                k -> new ArrayList<>()).add(u));
        return groups.values().stream().map(g -> new AiUsageUserDay(g.get(0).getUserId(), g.get(0).getDay(), cost(g),
                cost(g.stream().filter(u -> u.getAllocationId() == null).toList()), g.size(),
                g.stream().anyMatch(u -> u.getAllocationId() != null))).toList();
    }

    /** One row per allocation, with the cost and tokens of the active records its {@code aiUsageIds} cite. */
    static List<AllocationCoverage> coverage(List<AiUsageAllocation> allocations, List<AIUsage> usage) {
        Map<String, AIUsage> byId = usage.stream().filter(AIUsage::isActive).collect(Collectors.toMap(AIUsage::getId,
                u -> u, (a, b) -> a));
        return allocations.stream().map(a -> {
            List<AIUsage> found = a.getAiUsageIds() == null ? List.<AIUsage>of() : a.getAiUsageIds().stream()
                    .map(byId::get).filter(Objects::nonNull).toList();
            return new AllocationCoverage(a.getId(), a.getUserId(), a.getDay(), a.getSplits(), cost(found),
                    found.stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum());
        }).toList();
    }
}

package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixture: usage records summed per (user, day) the way {@code AIUsageRepository
 * .summarizeByUserAndDay} sums them in the database (cost with a missing value as 0, the cost of
 * records without an allocation, record count, any record claimed). Lets a test keep describing
 * usage as records while the code reads the summary.
 */
public final class AiUsageSummaries {

    private AiUsageSummaries() {}

    public static List<AiUsageUserDay> of(List<AIUsage> records) {
        Map<String, List<AIUsage>> byUserDay = new LinkedHashMap<>();
        for (AIUsage usage : records) {
            byUserDay.computeIfAbsent(usage.getUserId() + "|" + usage.getDay(), k -> new ArrayList<>()).add(usage);
        }
        return byUserDay.values().stream().map(AiUsageSummaries::summary).toList();
    }

    private static AiUsageUserDay summary(List<AIUsage> records) {
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal unallocated = BigDecimal.ZERO;
        boolean claimed = false;
        for (AIUsage usage : records) {
            BigDecimal value = usage.getCostUsd() == null ? BigDecimal.ZERO : usage.getCostUsd();
            cost = cost.add(value);
            if (usage.getAllocationId() == null) unallocated = unallocated.add(value);
            else claimed = true;
        }
        AIUsage first = records.getFirst();
        LocalDate day = first.getDay();
        return new AiUsageUserDay(first.getUserId(), day, cost, unallocated, records.size(), claimed);
    }
}

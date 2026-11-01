package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationDestination;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiDestination;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CategoryCost;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.OutOfDelivery;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PersonCost;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ToValidate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where the AI cost of a group of people went (uso-ia-fora-de-entrega, design 4 and T5): their total cost,
 * minus what {@code VALIDATED} allocations attribute to work items of the cut ({@code itemsUsd}), to other
 * projects, and to "Fora de entrega" (by category and by person); whatever is not validated yet (pending,
 * awaiting approval, not allocated) is "A validar". The pieces always add up to the total: values are
 * rounded to 6 places and the rounding closes on {@code otherProjectsUsd}.
 */
public final class AiDestinationCalculator {

    private static final int SCALE = 6;

    private AiDestinationCalculator() {}

    /**
     * @param rows        the validated and awaiting allocations of the people ({@code summarizeDestinationCoverage})
     * @param totalByUser the AI cost of each person in the window (the dashboard's own sums)
     * @param itemIds     work items of the cut (a project, or every visible project)
     * @param names       display name by user id; a missing one falls back to the id
     */
    public static AiDestination compute(List<AllocationDestination> rows, Set<String> itemIds,
                                        Map<String, BigDecimal> totalByUser, Map<String, String> names) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal cost : totalByUser.values()) total = total.add(orZero(cost));

        BigDecimal validated = BigDecimal.ZERO;
        BigDecimal items = BigDecimal.ZERO;
        BigDecimal awaiting = BigDecimal.ZERO;
        BigDecimal outOfDelivery = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        Map<String, BigDecimal> byPerson = new HashMap<>();
        for (AllocationDestination a : rows) {
            BigDecimal cost = orZero(a.costUsd());
            if (a.status() == AiUsageAllocation.Status.AWAITING_APPROVAL) {
                awaiting = awaiting.add(cost);
                continue;
            }
            if (a.status() != AiUsageAllocation.Status.VALIDATED) continue;
            validated = validated.add(cost);
            if (a.splits() == null) continue;
            for (AllocationSplit split : a.splits()) {
                if (split.getPercentage() == null) continue;
                BigDecimal part = cost.multiply(split.getPercentage());
                if (split.isOutOfDelivery()) {
                    outOfDelivery = outOfDelivery.add(part);
                    byCategory.merge(split.getCategory() == null ? AllocationSplit.Category.OTHER.name()
                            : split.getCategory().name(), part, BigDecimal::add);
                    byPerson.merge(a.userId(), part, BigDecimal::add);
                } else if (itemIds.contains(split.getWorkItemId())) {
                    items = items.add(part);
                }
            }
        }

        BigDecimal totalR = round(total);
        BigDecimal itemsR = round(items);
        BigDecimal oodR = round(outOfDelivery);
        BigDecimal toValidateR = round(total.subtract(validated).max(BigDecimal.ZERO));
        BigDecimal awaitingR = round(awaiting).min(toValidateR);
        BigDecimal otherR = totalR.subtract(itemsR).subtract(oodR).subtract(toValidateR);
        if (otherR.signum() < 0) { // rounding or a day claimed beyond the window: never a negative bucket
            itemsR = itemsR.add(otherR).max(BigDecimal.ZERO);
            otherR = totalR.subtract(itemsR).subtract(oodR).subtract(toValidateR).max(BigDecimal.ZERO);
        }

        List<CategoryCost> categories = byCategory.entrySet().stream()
                .map(e -> new CategoryCost(e.getKey(), round(e.getValue())))
                .sorted(Comparator.comparing(CategoryCost::usd).reversed().thenComparing(CategoryCost::category))
                .toList();
        List<PersonCost> people = byPerson.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .map(e -> person(e.getKey(), e.getValue(), orZero(totalByUser.get(e.getKey())), names))
                .sorted(Comparator.comparing(PersonCost::usd).reversed().thenComparing(PersonCost::name))
                .toList();
        return new AiDestination(totalR, itemsR, otherR, new OutOfDelivery(oodR, categories, people),
                new ToValidate(toValidateR, awaitingR));
    }

    private static PersonCost person(String userId, BigDecimal usd, BigDecimal total, Map<String, String> names) {
        Double share = total.signum() <= 0 ? null
                : usd.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
        return new PersonCost(userId, names.getOrDefault(userId, userId), round(usd), round(total), share);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

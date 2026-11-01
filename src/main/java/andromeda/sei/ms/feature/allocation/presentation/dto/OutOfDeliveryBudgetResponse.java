package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.math.BigDecimal;

/** {@code baseMonth} is the month of the base: the previous one, or the month itself when it had no cost; {@code usedUsd} = validated + awaiting. */
public record OutOfDeliveryBudgetResponse(String month, int capPercent, BigDecimal baseUsd, String baseKind,
                                          String baseMonth, BigDecimal capUsd, BigDecimal usedUsd,
                                          BigDecimal awaitingUsd) {

    /** {@code PREVIOUS_MONTH}, or {@code CURRENT_MONTH_TO_DATE} when the base fell back to the month itself. */
    public static String baseKindOf(String month, String baseMonth) {
        return month.equals(baseMonth) ? "CURRENT_MONTH_TO_DATE" : "PREVIOUS_MONTH";
    }
}

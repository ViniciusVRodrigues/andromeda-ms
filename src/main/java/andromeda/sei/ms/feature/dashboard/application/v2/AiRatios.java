package andromeda.sei.ms.feature.dashboard.application.v2;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single "cost per unit" division of the dashboards: AI cost per point of complexity delivered and
 * per hour of effort logged (complexity-effort D3).
 */
public final class AiRatios {

    /** Same scale the three private {@code perSp} helpers used. */
    public static final int MONEY_SCALE = 6;

    private AiRatios() {}

    /** {@code cost / divisor}, or {@code null} when the cost is absent or the divisor is absent or not positive. */
    public static BigDecimal per(BigDecimal cost, Double divisor) {
        if (cost == null || divisor == null || divisor <= 0) return null;
        return cost.divide(BigDecimal.valueOf(divisor), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** {@code hours / points} (hours per point of complexity), or {@code null} when either is absent or points is not positive. */
    public static Double hoursPer(Double hours, Double points) {
        if (hours == null || points == null || points <= 0) return null;
        return hours / points;
    }
}

package andromeda.sei.ms.shared.domain.metrics;

/** Percentages and hour durations with the rounding every metric shares. */
public final class Ratios {

    private static final double HUNDRED = 100.0;
    private static final double PERCENT_SCALE = 100.0;
    private static final double MILLIS_PER_HOUR = 3_600_000.0;
    private static final double HOURS_SCALE = 10_000.0;

    private Ratios() {}

    /** {@code 100 × part / whole}, rounded to 2 decimals; {@code null} when {@code whole} is zero. */
    public static Double percent(long part, long whole) {
        if (whole <= 0) return null;
        return Math.round(HUNDRED * part / whole * PERCENT_SCALE) / PERCENT_SCALE;
    }

    /** Hours between two instants, rounded to 4 decimals (the convention of every stored duration). */
    public static double hours(java.time.Instant from, java.time.Instant to) {
        double raw = (to.toEpochMilli() - from.toEpochMilli()) / MILLIS_PER_HOUR;
        return Math.round(raw * HOURS_SCALE) / HOURS_SCALE;
    }
}

package andromeda.sei.ms.shared.domain.metrics;

/**
 * An aggregated duration, in hours: the median ({@code p50}, shown on cards), the 90th percentile
 * ({@code p90}, shown in detail views) and the sample size {@code n}.
 *
 * <p>With no sample both percentiles are {@code null} and {@code n = 0} — "no data" must stay
 * distinct from a real zero duration.
 */
public record DurationStat(Double p50, Double p90, int n) {

    private static final DurationStat EMPTY = new DurationStat(null, null, 0);

    public static DurationStat empty() {
        return EMPTY;
    }
}

package andromeda.sei.ms.shared.domain.metrics;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Nearest-rank percentiles, the single aggregation every duration metric uses
 * (metric-aggregation-rules): sort the non-null values ascending and take the value at the
 * 1-indexed rank {@code ceil(p / 100 × n)}.
 *
 * <p>Nearest-rank always returns an observed value, so a p50 of "3 hours" is a duration some item
 * actually took — unlike an interpolated or averaged figure, which a single outlier can drag far
 * from anything that happened.
 */
public final class Percentiles {

    public static final int MEDIAN = 50;
    public static final int P90 = 90;
    private static final double HUNDRED = 100.0;

    private Percentiles() {}

    /** p50/p90/n over the non-null values; nulls are ignored, never counted as zero. */
    public static DurationStat of(Collection<Double> values) {
        if (values == null) return DurationStat.empty();
        List<Double> sorted = values.stream().filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) return DurationStat.empty();
        return new DurationStat(nearestRank(sorted, MEDIAN), nearestRank(sorted, P90), sorted.size());
    }

    /**
     * Nearest-rank {@code percentile} (1–100) over the non-null values; {@code null} with no sample.
     * For references other than p50/p90 — e.g. the p85 cycle time an aging item is compared to.
     */
    public static Double percentile(Collection<Double> values, int percentile) {
        if (values == null) return null;
        List<Double> sorted = values.stream().filter(Objects::nonNull).sorted().toList();
        return sorted.isEmpty() ? null : nearestRank(sorted, percentile);
    }

    /** @param sortedValues ascending, non-empty, without nulls */
    static double nearestRank(List<Double> sortedValues, int percentile) {
        int rank = (int) Math.ceil(percentile / HUNDRED * sortedValues.size());
        int index = Math.max(1, rank) - 1;
        return sortedValues.get(index);
    }
}

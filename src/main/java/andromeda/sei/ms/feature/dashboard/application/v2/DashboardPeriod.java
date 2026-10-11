package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.dashboard.domain.exception.DashboardPeriodInvalidException;

import java.time.LocalDate;
import java.util.Set;

/**
 * The period of a v2 project dashboard (dashboards-customizaveis D1): the last {@code days} days up
 * to today (inclusive) and the previous period of the same size right before it.
 *
 * @param current  {@code [today - (days - 1), today]}
 * @param previous {@code [current.first - days, current.first - 1]}
 */
public record DashboardPeriod(int days, DayRange current, DayRange previous) {

    public static final int DEFAULT_DAYS = 90;
    private static final Set<Integer> ALLOWED_DAYS = Set.of(30, 90, 180, 365);

    /**
     * The {@code periodDays} request parameter: absent → {@value #DEFAULT_DAYS}; anything but 30,
     * 90, 180 or 365 → {@link DashboardPeriodInvalidException} (422).
     */
    public static int requireValidDays(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_DAYS;
        try {
            int days = Integer.parseInt(raw.trim());
            if (ALLOWED_DAYS.contains(days)) return days;
        } catch (NumberFormatException ignored) {
            // falls through to the 422 below
        }
        throw new DashboardPeriodInvalidException(raw);
    }

    public static DashboardPeriod of(int days, LocalDate today) {
        if (!ALLOWED_DAYS.contains(days)) throw new DashboardPeriodInvalidException(String.valueOf(days));
        LocalDate start = today.minusDays(days - 1L);
        return new DashboardPeriod(days, new DayRange(start, today),
                new DayRange(start.minusDays(days), start.minusDays(1)));
    }

    public PeriodBuckets buckets() {
        return PeriodBuckets.of(current);
    }
}

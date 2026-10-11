package andromeda.sei.ms.feature.dashboard.application.v2;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * The time series buckets of a period, as the v2 preview's {@code bucketsFor}: weekly (weeks start
 * on Monday, label {@code dd/MM}) up to {@value #WEEKLY_MAX_SPAN_DAYS} days, monthly above (label
 * {@code mmm/yy} in pt-BR). The leading bucket that would start before the period is dropped (a
 * partial bar would read as a drop); the last one, in progress, ends today.
 *
 * @param granularity {@code "semana"} or {@code "mês"} — the preview renders it as-is
 */
public record PeriodBuckets(String granularity, List<Bucket> buckets) {

    static final long WEEKLY_MAX_SPAN_DAYS = 92;
    static final String WEEKLY = "semana";
    static final String MONTHLY = "mês";
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final List<String> MONTHS =
            List.of("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez");
    private static final int YEAR_DIGITS = 100;

    public static PeriodBuckets of(DayRange period) {
        boolean weekly = ChronoUnit.DAYS.between(period.first(), period.last()) <= WEEKLY_MAX_SPAN_DAYS;
        List<Bucket> all = weekly ? weeks(period) : months(period);
        List<Bucket> full = all.stream().filter(b -> !b.range().first().isBefore(period.first())).toList();
        return new PeriodBuckets(weekly ? WEEKLY : MONTHLY, full.isEmpty() ? all : full);
    }

    private static List<Bucket> weeks(DayRange period) {
        List<Bucket> buckets = new ArrayList<>();
        LocalDate monday = period.first().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate start = monday; !start.isAfter(period.last()); start = start.plusWeeks(1)) {
            buckets.add(new Bucket(clamp(start, start.plusDays(6), period), start.format(WEEK_LABEL)));
        }
        return buckets;
    }

    private static List<Bucket> months(DayRange period) {
        List<Bucket> buckets = new ArrayList<>();
        for (LocalDate start = period.first().withDayOfMonth(1); !start.isAfter(period.last());
             start = start.plusMonths(1)) {
            String label = MONTHS.get(start.getMonthValue() - 1) + "/"
                    + String.format("%02d", start.getYear() % YEAR_DIGITS);
            buckets.add(new Bucket(clamp(start, start.with(TemporalAdjusters.lastDayOfMonth()), period), label));
        }
        return buckets;
    }

    private static DayRange clamp(LocalDate start, LocalDate end, DayRange period) {
        return new DayRange(start, end.isAfter(period.last()) ? period.last() : end);
    }

    /** One bar of a series: its days and its label. */
    public record Bucket(DayRange range, String label) {

        /** ISO day the bucket starts on ({@code yyyy-MM-dd}). */
        public String start() {
            return range.first().toString();
        }
    }
}

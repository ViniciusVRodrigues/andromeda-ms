package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.dashboard.domain.exception.DashboardPeriodInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * dashboards-customizaveis 1.10 — {@code periodDays} ∈ {30, 90, 180, 365} (422
 * {@code DASHBOARD_PERIOD_INVALID} otherwise), the period and the previous one of the same size,
 * and the buckets of the v2 preview ({@code bucketsFor}): weekly up to 92 days, monthly above, the
 * leading partial bucket dropped. Today = Sunday 2026-10-11.
 */
class DashboardPeriodTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-11");

    @Test
    void should_default90_when_periodIsAbsent() {
        assertThat(DashboardPeriod.requireValidDays(null)).isEqualTo(90);
        assertThat(DashboardPeriod.requireValidDays(" ")).isEqualTo(90);
    }

    @ParameterizedTest
    @ValueSource(strings = {"30", "90", "180", "365"})
    void should_acceptTheAllowedPeriods(String raw) {
        assertThat(DashboardPeriod.requireValidDays(raw)).isEqualTo(Integer.parseInt(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"45", "0", "-30", "abc", "90.0", "366"})
    void should_throwDashboardPeriodInvalid_when_periodIsNotAllowed(String raw) {
        assertThatThrownBy(() -> DashboardPeriod.requireValidDays(raw))
                .isInstanceOf(DashboardPeriodInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "DASHBOARD_PERIOD_INVALID");
    }

    @Test
    void should_rejectInvalidDays_inTheFactoryToo() {
        assertThatThrownBy(() -> DashboardPeriod.of(45, TODAY)).isInstanceOf(DashboardPeriodInvalidException.class);
    }

    @Test
    void should_spanTheLastNDays_andThePreviousPeriodOfTheSameSize() {
        DashboardPeriod period = DashboardPeriod.of(90, TODAY);

        assertThat(period.current()).isEqualTo(new DayRange(LocalDate.parse("2026-07-14"), TODAY));
        assertThat(period.previous()).isEqualTo(new DayRange(LocalDate.parse("2026-04-15"), LocalDate.parse("2026-07-13")));
        assertThat(period.current().from()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(period.current().to()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
        assertThat(period.current().contains(Instant.parse("2026-10-11T23:59:59Z"))).isTrue();
        assertThat(period.current().contains(Instant.parse("2026-07-13T23:59:59Z"))).isFalse();
        assertThat(period.previous().contains(Instant.parse("2026-07-13T23:59:59Z"))).isTrue();
    }

    @Test
    void should_bucketWeekly_upTo90Days_droppingTheLeadingPartialWeek() {
        PeriodBuckets buckets = DashboardPeriod.of(90, TODAY).buckets();

        assertThat(buckets.granularity()).isEqualTo("semana");
        assertThat(buckets.buckets()).hasSize(12);
        assertThat(buckets.buckets().getFirst().range().first()).isEqualTo(LocalDate.parse("2026-07-20"));
        assertThat(buckets.buckets().getFirst().label()).isEqualTo("20/07");
        assertThat(buckets.buckets().getLast().range()).isEqualTo(new DayRange(LocalDate.parse("2026-10-05"), TODAY));
    }

    @Test
    void should_bucketWeekly_for30Days() {
        PeriodBuckets buckets = DashboardPeriod.of(30, TODAY).buckets();

        assertThat(buckets.buckets()).extracting(PeriodBuckets.Bucket::label)
                .containsExactly("14/09", "21/09", "28/09", "05/10");
    }

    @Test
    void should_bucketMonthly_above92Days_withPortugueseLabels_andTheCurrentMonthClampedToToday() {
        PeriodBuckets buckets = DashboardPeriod.of(180, TODAY).buckets();

        assertThat(buckets.granularity()).isEqualTo("mês");
        assertThat(buckets.buckets()).extracting(PeriodBuckets.Bucket::label)
                .containsExactly("mai/26", "jun/26", "jul/26", "ago/26", "set/26", "out/26");
        assertThat(buckets.buckets().getFirst().range())
                .isEqualTo(new DayRange(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")));
        assertThat(buckets.buckets().getLast().range()).isEqualTo(new DayRange(LocalDate.parse("2026-10-01"), TODAY));
    }

    @Test
    void should_bucketMonthly_for365Days_acrossTheYear() {
        PeriodBuckets buckets = DashboardPeriod.of(365, TODAY).buckets();

        assertThat(buckets.buckets()).hasSize(12);
        assertThat(buckets.buckets().getFirst().label()).isEqualTo("nov/25");
        assertThat(buckets.buckets().getLast().label()).isEqualTo("out/26");
    }
}

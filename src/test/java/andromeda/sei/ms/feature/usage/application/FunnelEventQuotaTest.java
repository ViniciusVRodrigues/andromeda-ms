package andromeda.sei.ms.feature.usage.application;

import andromeda.sei.ms.feature.usage.application.FunnelEventQuota.Verdict;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FunnelEventQuotaTest {

    private static final Instant NOON = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void should_capEachFunnelAt200EventsPerDay() {
        FunnelEventQuota quota = new FunnelEventQuota(Clock.fixed(NOON, ZoneOffset.UTC));

        for (int i = 0; i < 10; i++) assertThat(quota.admit("f1", 20)).isEqualTo(Verdict.ADMITTED);
        assertThat(quota.admit("f1", 1)).isEqualTo(Verdict.FUNNEL_LIMIT);
        assertThat(quota.admit("f2", 20)).isEqualTo(Verdict.ADMITTED);
    }

    @Test
    void should_notChargeARejectedBatch() {
        FunnelEventQuota quota = new FunnelEventQuota(Clock.fixed(NOON, ZoneOffset.UTC));
        quota.admit("f1", 190);

        assertThat(quota.admit("f1", 20)).isEqualTo(Verdict.FUNNEL_LIMIT);
        assertThat(quota.admit("f1", 10)).isEqualTo(Verdict.ADMITTED);
    }

    @Test
    void should_discardEverything_afterThe5000GlobalCeiling() {
        FunnelEventQuota quota = new FunnelEventQuota(Clock.fixed(NOON, ZoneOffset.UTC));
        for (int i = 0; i < 250; i++) assertThat(quota.admit("funnel-" + i, 20)).isEqualTo(Verdict.ADMITTED);

        assertThat(quota.admit("novo", 1)).isEqualTo(Verdict.GLOBAL_CEILING);
        assertThat(quota.admit("novo", 1)).isEqualTo(Verdict.GLOBAL_CEILING);
    }

    @Test
    void should_resetAtUtcMidnight() {
        MutableClock clock = new MutableClock(NOON);
        FunnelEventQuota quota = new FunnelEventQuota(clock);
        quota.admit("f1", 200);
        assertThat(quota.admit("f1", 1)).isEqualTo(Verdict.FUNNEL_LIMIT);

        clock.now = Instant.parse("2026-10-21T00:00:01Z");

        assertThat(quota.admit("f1", 1)).isEqualTo(Verdict.ADMITTED);
    }

    @Test
    void should_tellHowLongUntilTheReset() {
        FunnelEventQuota quota = new FunnelEventQuota(Clock.fixed(NOON, ZoneOffset.UTC));

        assertThat(quota.secondsUntilReset()).isEqualTo(12 * 3600);
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

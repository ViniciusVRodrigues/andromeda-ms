package andromeda.sei.ms.feature.sync.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SyncCooldownServiceTest {

    private MutableClock clock;
    private SyncCooldownService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
        service = new SyncCooldownService(clock);
    }

    @Test
    void shouldAdmitBindingThatWasNeverSynced() {
        assertThat(service.tryAcquire("bind-1")).isZero();
    }

    @Test
    void shouldRejectSameBindingWithinThirtySecondsReportingRemainingSeconds() {
        service.tryAcquire("bind-1");
        clock.advance(Duration.ofSeconds(12));

        assertThat(service.tryAcquire("bind-1")).isEqualTo(18);
    }

    @Test
    void shouldNotResetCooldownWhenAdmissionIsRejected() {
        service.tryAcquire("bind-1");
        clock.advance(Duration.ofSeconds(20));
        service.tryAcquire("bind-1");
        clock.advance(Duration.ofSeconds(10));

        assertThat(service.tryAcquire("bind-1")).isZero();
    }

    @Test
    void shouldAdmitDifferentBindingsIndependently() {
        service.tryAcquire("bind-1");
        clock.advance(Duration.ofSeconds(5));

        assertThat(service.tryAcquire("bind-2")).isZero();
    }

    @Test
    void shouldRoundRemainingSecondsUpSoRetryAfterIsNeverZero() {
        service.tryAcquire("bind-1");
        clock.advance(Duration.ofMillis(29_500));

        assertThat(service.remainingSeconds("bind-1")).isEqualTo(1);
    }

    @Test
    void shouldFreeBindingWhenAdmissionIsReleased() {
        service.tryAcquire("bind-1");
        service.release("bind-1");

        assertThat(service.tryAcquire("bind-1")).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

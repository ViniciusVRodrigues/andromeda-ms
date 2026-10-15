package andromeda.sei.ms.shared.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRefuses() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThatNoException().isThrownBy(() -> limiter.check("k", 3, Duration.ofMinutes(1)));
        }
        assertThatThrownBy(() -> limiter.check("k", 3, Duration.ofMinutes(1)))
                .isInstanceOf(RateLimiter.TooManyAttemptsException.class);
    }

    @Test
    void countsEachKeySeparately() {
        RateLimiter limiter = new RateLimiter();
        limiter.check("a", 1, Duration.ofMinutes(1));
        assertThatNoException()
                .as("uma chave estourada nao pode bloquear outra")
                .isThrownBy(() -> limiter.check("b", 1, Duration.ofMinutes(1)));
    }

    @Test
    void forgetsAttemptsOnceTheWindowPasses() {
        RateLimiter limiter = new RateLimiter();
        limiter.check("k", 1, Duration.ofMillis(1));
        assertThatNoException()
                .isThrownBy(() -> {
                    Thread.sleep(5);
                    limiter.check("k", 1, Duration.ofMillis(1));
                });
    }

    @Test
    void tryAcquire_returnsFalseInsteadOfThrowing_andDoesNotCountRefusedCalls() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("other", 2, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void retryAfterSeconds_isBetweenOneAndTheWindow() {
        RateLimiter limiter = new RateLimiter();
        assertThat(limiter.retryAfterSeconds("none", Duration.ofMinutes(10))).isEqualTo(1);
        limiter.tryAcquire("k", 1, Duration.ofMinutes(10));
        assertThat(limiter.retryAfterSeconds("k", Duration.ofMinutes(10))).isBetween(590L, 600L);
    }
}

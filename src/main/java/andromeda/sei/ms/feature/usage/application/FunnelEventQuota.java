package andromeda.sei.ms.feature.usage.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Daily (UTC) in-memory budget of the anonymous funnel endpoint: at most 200 events per funnelId and
 * 5.000 events per instance. Per instance, like {@code RateLimiter}; resets at UTC midnight. The map only
 * grows with admitted events, so the global ceiling also bounds its size.
 */
@Component
public class FunnelEventQuota {

    public static final int MAX_EVENTS_PER_FUNNEL_PER_DAY = 200;
    public static final int MAX_EVENTS_PER_DAY = 5_000;

    public enum Verdict { ADMITTED, GLOBAL_CEILING, FUNNEL_LIMIT }

    private static final Logger log = LoggerFactory.getLogger(FunnelEventQuota.class);

    private final Clock clock;
    private final Map<String, Integer> perFunnel = new HashMap<>();
    private LocalDate day;
    private int total;
    private boolean warned;

    @Autowired
    public FunnelEventQuota() {
        this(Clock.systemUTC());
    }

    FunnelEventQuota(Clock clock) {
        this.clock = clock;
    }

    /** Charges {@code count} events to the funnel and to the instance, unless one budget would overflow. */
    public synchronized Verdict admit(String funnelId, int count) {
        rollDay();
        if (total + count > MAX_EVENTS_PER_DAY) {
            if (!warned) {
                warned = true;
                log.warn("Funnel telemetry daily ceiling ({}) reached on this instance; discarding until UTC midnight",
                        MAX_EVENTS_PER_DAY);
            }
            return Verdict.GLOBAL_CEILING;
        }
        int used = perFunnel.getOrDefault(funnelId, 0);
        if (used + count > MAX_EVENTS_PER_FUNNEL_PER_DAY) return Verdict.FUNNEL_LIMIT;
        perFunnel.put(funnelId, used + count);
        total += count;
        return Verdict.ADMITTED;
    }

    /** Seconds until the daily budgets reset (at least 1). */
    public synchronized long secondsUntilReset() {
        rollDay();
        long seconds = Duration.between(clock.instant(),
                day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)).toSeconds();
        return Math.max(1, seconds);
    }

    private void rollDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            perFunnel.clear();
            total = 0;
            warned = false;
        }
    }
}

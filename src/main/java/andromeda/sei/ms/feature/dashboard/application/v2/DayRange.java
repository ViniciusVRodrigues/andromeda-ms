package andromeda.sei.ms.feature.dashboard.application.v2;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Calendar days {@code [first, last]} (inclusive, UTC — AI usage is stored per day) and the same
 * span as half-open instants {@code [from, to)} (work items, pull requests, commits).
 */
public record DayRange(LocalDate first, LocalDate last) {

    public DayRange {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(last, "last");
    }

    public Instant from() {
        return first.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant to() {
        return last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public boolean contains(Instant at) {
        return at != null && !at.isBefore(from()) && at.isBefore(to());
    }

    public boolean containsDay(LocalDate day) {
        return day != null && !day.isBefore(first) && !day.isAfter(last);
    }
}

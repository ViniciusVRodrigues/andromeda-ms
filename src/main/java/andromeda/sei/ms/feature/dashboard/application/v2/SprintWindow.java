package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.domain.Sprint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * The span of a sprint in the two forms the data needs: calendar days {@code [startDay, endDay]}
 * (inclusive, UTC — AI usage is stored per day) and instants {@code [from, to)} (half-open — pull
 * requests, commits and reviews).
 */
public record SprintWindow(LocalDate startDay, LocalDate endDay, Instant from, Instant to) {

    /** @param sprint a sprint with both dates */
    public static SprintWindow of(Sprint sprint) {
        Objects.requireNonNull(sprint.getStartDate(), "sprint.startDate");
        Objects.requireNonNull(sprint.getEndDate(), "sprint.endDate");
        LocalDate start = toDay(sprint.getStartDate());
        LocalDate end = toDay(sprint.getEndDate());
        return new SprintWindow(start, end, start.atStartOfDay(ZoneOffset.UTC).toInstant(),
                end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    public boolean contains(Instant at) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }

    public boolean containsDay(LocalDate day) {
        return day != null && !day.isBefore(startDay) && !day.isAfter(endDay);
    }

    /** The last day with data so far: {@code min(endDay, today)}. */
    public LocalDate lastElapsedDay(LocalDate today) {
        return endDay.isBefore(today) ? endDay : today;
    }

    public static LocalDate toDay(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}

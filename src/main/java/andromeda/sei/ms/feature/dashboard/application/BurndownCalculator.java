package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The sprint burndown rule shared by the developer dashboard and the v2 dashboards: one point per
 * calendar day from the sprint start up to {@code min(sprintEnd, today)}; remaining = effective
 * effort of the deliverable units minus the effort of the units completed by the end of that day;
 * ideal = a straight line from the total effort on the first day to zero on the last.
 */
public final class BurndownCalculator {

    private BurndownCalculator() {}

    /** Empty when the sprint has no dates or lasts less than a day. */
    public static List<BurndownPoint> compute(List<WorkItem> units, EffortBearingLevelResolver.EffortScope scope,
                                              LocalDate sprintStart, LocalDate sprintEnd, LocalDate today) {
        if (sprintStart == null || sprintEnd == null) return List.of();
        if (ChronoUnit.DAYS.between(sprintStart, sprintEnd) <= 0) return List.of();

        Double resolvedTotal = scope.totalEffort(units);
        double totalSp = resolvedTotal == null ? 0d : resolvedTotal;
        LocalDate seriesEnd = sprintEnd.isBefore(today) ? sprintEnd : today;

        List<BurndownPoint> series = new ArrayList<>();
        for (LocalDate day = sprintStart; !day.isAfter(seriesEnd); day = day.plusDays(1)) {
            Double completed = scope.totalEffort(completedBy(units, day));
            double remaining = totalSp - (completed == null ? 0d : completed);
            series.add(new BurndownPoint(day, remaining, ideal(totalSp, sprintStart, sprintEnd, day)));
        }
        return series;
    }

    /** Ideal remaining effort on {@code day}; {@code sprintEnd} must be after {@code sprintStart}. */
    public static double ideal(double totalSp, LocalDate sprintStart, LocalDate sprintEnd, LocalDate day) {
        long totalSprintDays = ChronoUnit.DAYS.between(sprintStart, sprintEnd);
        long daysElapsed = ChronoUnit.DAYS.between(sprintStart, day);
        return totalSp * (1.0 - (double) daysElapsed / totalSprintDays);
    }

    private static List<WorkItem> completedBy(List<WorkItem> units, LocalDate day) {
        return units.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .filter(wi -> wi.getCompletedAt() != null)
                .filter(wi -> !toLocalDate(wi.getCompletedAt()).isAfter(day))
                .toList();
    }

    private static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}

package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver.EffortSummary;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The active work items and sprints of one project, read once per request, with the slices the
 * period views share.
 *
 * <ul>
 *   <li>{@link #effort()}: the single velocity definition over the whole project
 *       ({@link EffortBearingLevelResolver#summarize}).</li>
 *   <li>{@link #doneUnitsIn}: deliverable units completed in a range. Epics and features are left
 *       out: over a whole project they are containers whose effective effort is the sum of their
 *       children, already counted through the children.</li>
 *   <li>{@link #sprintsEndingIn}: past sprints (one per shared iteration) that ended in a range,
 *       with committed × delivered by the same definition as the team dashboard.</li>
 * </ul>
 */
public final class ProjectPeriodItems {

    private static final double PERCENT = 100.0;
    private static final Set<WorkItem.ResolvedType> CONTAINERS =
            Set.of(WorkItem.ResolvedType.EPIC, WorkItem.ResolvedType.FEATURE);

    private final List<WorkItem> all;
    private final EffortSummary effort;
    private final List<SprintFigures> sprints;

    private ProjectPeriodItems(List<WorkItem> all, EffortSummary effort, List<SprintFigures> sprints) {
        this.all = all;
        this.effort = effort;
        this.sprints = sprints;
    }

    /** @param items and @param sprints of the project; inactive ones are dropped here */
    public static ProjectPeriodItems of(Collection<WorkItem> items, Collection<Sprint> sprints,
                                        EffortBearingLevelResolver resolver) {
        List<WorkItem> active = items.stream().filter(Objects::nonNull).filter(WorkItem::isActive).toList();
        Map<String, List<WorkItem>> bySprint = active.stream().filter(i -> i.getSprintId() != null)
                .collect(Collectors.groupingBy(WorkItem::getSprintId));
        List<SprintFigures> figures = SharedIterations.distinct(sprints.stream().filter(Sprint::isActive).toList())
                .stream()
                .filter(s -> s.getId() != null && s.getStartDate() != null && s.getEndDate() != null)
                .sorted(Comparator.comparing(Sprint::getStartDate))
                .map(s -> SprintFigures.of(s, bySprint.getOrDefault(s.getId(), List.of()), resolver))
                .toList();
        return new ProjectPeriodItems(active, resolver.summarize(active), figures);
    }

    /**
     * Earliest start among the past sprints (one per shared iteration) whose last day falls in
     * {@code range} — the same sprints as {@link #sprintsEndingIn}, from the sprints alone.
     */
    public static java.util.Optional<java.time.LocalDate> earliestStartOfPastSprintsEndingIn(Collection<Sprint> sprints,
                                                                                         DayRange range) {
        return SharedIterations.distinct(sprints.stream().filter(Sprint::isActive).toList()).stream()
                .filter(s -> s.getId() != null && s.getStartDate() != null && s.getEndDate() != null)
                .filter(s -> s.getTimeFrame() == Sprint.TimeFrame.PAST)
                .map(SprintWindow::of)
                .filter(w -> range.containsDay(w.endDay()))
                .map(SprintWindow::startDay)
                .min(Comparator.naturalOrder());
    }

    public List<WorkItem> all() {
        return all;
    }

    public Set<String> ids() {
        return all.stream().map(WorkItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public EffortSummary effort() {
        return effort;
    }

    public static boolean isContainer(WorkItem item) {
        return item.getResolvedType() != null && CONTAINERS.contains(item.getResolvedType());
    }

    /** Azure priority (stored as text) as a number; {@code null} when absent or not a number. */
    public static Integer priority(WorkItem item) {
        if (item.getPriority() == null) return null;
        try {
            return Integer.valueOf(item.getPriority().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<WorkItem> doneUnitsIn(DayRange range) {
        return effort.doneUnits().stream()
                .filter(u -> !isContainer(u) && range.contains(u.getCompletedAt()))
                .toList();
    }

    /**
     * Story-tier units (complexity-effort D1) completed in a range, containers left out like in
     * {@link #doneUnitsIn}; empty when the project has no story tier.
     */
    public List<WorkItem> doneComplexityUnitsIn(DayRange range) {
        return effort.complexityUnits().stream()
                .filter(u -> WorkItem.ResolvedStatus.DONE.equals(u.getResolvedStatus()))
                .filter(u -> !isContainer(u) && range.contains(u.getCompletedAt()))
                .toList();
    }

    /** Complexity delivered in a range; {@code null} when no unit of the project carries complexity. */
    public Double complexityDeliveredIn(DayRange range) {
        if (effort.plannedComplexity() == null) return null;
        Double sum = effort.scope().totalComplexity(doneComplexityUnitsIn(range));
        return sum == null ? Double.valueOf(0d) : sum;
    }

    /** Hours logged on the Tasks of the units delivered in a range (complexity-effort D2). */
    public Double effortLoggedIn(DayRange range) {
        List<WorkItem> units = effort.complexityUnits().isEmpty() ? doneUnitsIn(range) : doneComplexityUnitsIn(range);
        return effort.scope().totalTaskEffort(units);
    }

    /** Past sprints whose last day falls in {@code range}, oldest first. */
    public List<SprintFigures> sprintsEndingIn(DayRange range) {
        return sprints.stream()
                .filter(f -> f.sprint().getTimeFrame() == Sprint.TimeFrame.PAST && range.containsDay(f.window().endDay()))
                .toList();
    }

    /** Sprints overlapping {@code range} (any time frame), oldest first. */
    public List<SprintFigures> sprintsOverlapping(DayRange range) {
        return sprints.stream()
                .filter(f -> !f.window().startDay().isAfter(range.last()) && !f.window().endDay().isBefore(range.first()))
                .toList();
    }

    public Optional<Sprint> latestSprint() {
        return sprints.isEmpty() ? Optional.empty() : Optional.of(sprints.getLast().sprint());
    }

    /**
     * Committed × delivered of one sprint: {@code planned}/{@code delivered} by
     * {@link EffortBearingLevelResolver#summarize}; {@code complexityPlanned}/{@code complexityDelivered} are the
     * same in complexity and {@code predictabilityPct} = complexity delivered ÷ planned × 100 (complexity-effort D1),
     * {@code null} without planned complexity.
     */
    public record SprintFigures(Sprint sprint, SprintWindow window, List<WorkItem> items, EffortSummary summary,
                                Double planned, Double delivered, Double predictabilityPct,
                                Double complexityPlanned, Double complexityDelivered) {

        static SprintFigures of(Sprint sprint, List<WorkItem> items, EffortBearingLevelResolver resolver) {
            EffortSummary summary = resolver.summarize(items);
            Double delivered = DashboardV2Views.deliveredOf(summary.planned(), summary.delivered());
            Double complexityDelivered = DashboardV2Views.deliveredOf(summary.plannedComplexity(),
                    summary.deliveredComplexity());
            Double pct = summary.plannedComplexity() == null || summary.plannedComplexity() <= 0
                    || complexityDelivered == null ? null
                    : complexityDelivered / summary.plannedComplexity() * PERCENT;
            return new SprintFigures(sprint, SprintWindow.of(sprint), items, summary, summary.planned(), delivered,
                    pct, summary.plannedComplexity(), complexityDelivered);
        }
    }
}

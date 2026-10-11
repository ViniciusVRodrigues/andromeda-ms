package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.shared.domain.metrics.Ratios;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The state category of one work item over time, from its recorded transitions (team flow,
 * dashboards-customizaveis 1.13). Only active transitions with a date count, in chronological order.
 *
 * <ul>
 *   <li>With transitions: the category at an instant is the target of the last transition up to it;
 *       before the first one the item is {@code TODO} (the history starts at creation).</li>
 *   <li>Without transitions (not synced with history yet): the item's own dates —
 *       {@code completedAt} reached → {@code DONE}, {@code startedAt} reached → {@code IN_PROGRESS},
 *       else {@code TODO}.</li>
 * </ul>
 */
public final class WorkItemStateTimeline {

    private final WorkItem item;
    private final List<WorkItemTransition> transitions;

    private WorkItemStateTimeline(WorkItem item, List<WorkItemTransition> transitions) {
        this.item = item;
        this.transitions = transitions;
    }

    public static WorkItemStateTimeline of(WorkItem item, List<WorkItemTransition> transitions) {
        Objects.requireNonNull(item, "item");
        List<WorkItemTransition> ordered = transitions == null ? List.of() : transitions.stream()
                .filter(Objects::nonNull).filter(WorkItemTransition::isActive)
                .filter(t -> t.getOccurredAt() != null && t.getToResolvedStatus() != null)
                .sorted(Comparator.comparing(WorkItemTransition::getOccurredAt))
                .toList();
        return new WorkItemStateTimeline(item, ordered);
    }

    public ResolvedStatus categoryAt(Instant at) {
        if (transitions.isEmpty()) return categoryFromDates(at);
        ResolvedStatus category = ResolvedStatus.TODO;
        for (WorkItemTransition t : transitions) {
            if (t.getOccurredAt().isAfter(at)) break;
            category = t.getToResolvedStatus();
        }
        return category;
    }

    /**
     * Hours spent in {@code IN_PROGRESS} up to the first completion ({@code completedAt}); without
     * transitions, the item's cycle time. {@code null} for an item never completed.
     */
    public Double inProgressHours() {
        Instant end = item.getCompletedAt();
        if (end == null) return null;
        if (transitions.isEmpty()) return item.getCycleTimeHours();
        double hours = 0;
        for (int i = 0; i < transitions.size(); i++) {
            WorkItemTransition t = transitions.get(i);
            if (!t.getOccurredAt().isBefore(end)) break;
            if (t.getToResolvedStatus() != ResolvedStatus.IN_PROGRESS) continue;
            Instant next = i + 1 < transitions.size() ? transitions.get(i + 1).getOccurredAt() : end;
            hours += Ratios.hours(t.getOccurredAt(), next.isBefore(end) ? next : end);
        }
        return hours;
    }

    private ResolvedStatus categoryFromDates(Instant at) {
        if (reached(item.getCompletedAt(), at)) return ResolvedStatus.DONE;
        if (reached(item.getStartedAt(), at)) return ResolvedStatus.IN_PROGRESS;
        return ResolvedStatus.TODO;
    }

    private static boolean reached(Instant moment, Instant at) {
        return moment != null && !moment.isAfter(at);
    }
}

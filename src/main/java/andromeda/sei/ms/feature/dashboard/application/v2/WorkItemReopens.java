package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reopens of work items from their recorded state transitions: a transition whose predecessor (by
 * {@code occurredAt}) ended in {@code DONE} and that itself does not — "concluído → outro". A
 * transition only stores the resolved category of its target, so the source category is the target
 * of the transition before it. Inactive transitions are ignored.
 */
public final class WorkItemReopens {

    private static final Comparator<WorkItemTransition> CHRONOLOGICAL = Comparator.comparing(
            WorkItemTransition::getOccurredAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()));

    private WorkItemReopens() {}

    /** Reopens of one item's transitions. */
    public static int count(Collection<WorkItemTransition> transitions) {
        List<WorkItemTransition> ordered = transitions.stream()
                .filter(Objects::nonNull).filter(WorkItemTransition::isActive)
                .sorted(CHRONOLOGICAL).toList();
        int reopens = 0;
        for (int i = 1; i < ordered.size(); i++) {
            boolean wasDone = ordered.get(i - 1).getToResolvedStatus() == WorkItem.ResolvedStatus.DONE;
            boolean leftDone = ordered.get(i).getToResolvedStatus() != WorkItem.ResolvedStatus.DONE;
            if (wasDone && leftDone) reopens++;
        }
        return reopens;
    }

    /** Reopens per work item id, for transitions of several items. */
    public static Map<String, Integer> byWorkItem(Collection<WorkItemTransition> transitions) {
        return transitions.stream().filter(t -> t.getWorkItemId() != null)
                .collect(Collectors.groupingBy(WorkItemTransition::getWorkItemId,
                        Collectors.collectingAndThen(Collectors.toList(), WorkItemReopens::count)));
    }
}

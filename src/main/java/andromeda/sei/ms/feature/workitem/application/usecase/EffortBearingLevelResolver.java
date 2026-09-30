package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.EffortBearingLevel;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which work items count as the effort-bearing deliverable unit for an organization, and
 * computes each unit's effective effort without double counting.
 *
 * <p>Replaces the {@code isStoryTierWork} predicate that was copy-pasted across both dashboards,
 * the strategic dashboard and both allocation use cases. That predicate matched STORY or FEATURE
 * and excluded TASK, which silently zeroed velocity/burndown for teams that put effort on Tasks.
 *
 * <h2>Effective effort: own value wins, children only fill the gap</h2>
 * A Story's own effort is authoritative — it is the number the team agreed on in planning. Its
 * child Tasks' effort is a more granular restatement of the same work, so summing both would
 * double count the same delivery. Therefore:
 * <ul>
 *   <li>Story has effort → use it; the Tasks' sum is complementary information only.</li>
 *   <li>Story has none → fall back to the sum of its Tasks (model 2).</li>
 *   <li>No Story tier at all → the Tasks are themselves the deliverable units (model 3).</li>
 * </ul>
 *
 * <h2>Single velocity definition (metrics-correctness)</h2>
 * {@link #summarize} is the one definition every surface uses — developer, team and strategic
 * dashboards and {@code sprint_metrics}: velocity is the effective effort of the deliverable units
 * in {@code DONE}; planned effort is the effective effort of all deliverable units of the scope.
 *
 * <p>The children index is built once per scope ({@link #scope}); before, every
 * {@code effectiveEffort} call rebuilt it, making a total over n units O(n²).
 */
@Component
public class EffortBearingLevelResolver {

    /**
     * Infers the level in use from the data when the org hasn't pinned one.
     *
     * <p>Inference is intentionally conservative: it only concludes TASK-only when there is
     * genuinely no story-tier effort to be found, because misclassifying model 1 as model 3 would
     * change every historical velocity number.
     */
    public EffortBearingLevel resolve(EffortBearingLevel configured, List<WorkItem> items) {
        if (configured != null && configured != EffortBearingLevel.AUTO) return configured;

        boolean anyStoryEffort = items.stream()
                .filter(wi -> isStoryTier(wi.getResolvedType()))
                .anyMatch(wi -> wi.getEffort() != null);
        boolean anyTaskEffort = items.stream()
                .filter(wi -> isTaskTier(wi.getResolvedType()))
                .anyMatch(wi -> wi.getEffort() != null);

        if (anyStoryEffort && anyTaskEffort) return EffortBearingLevel.STORY_AND_TASK;
        if (anyTaskEffort) return EffortBearingLevel.TASK;
        return EffortBearingLevel.STORY;
    }

    /**
     * The items that count as deliverable units for velocity/burndown/AI allocation.
     *
     * <p>For TASK the units are the Tasks themselves. For every other level the units are the
     * story-tier items — under STORY_AND_TASK a Task's effort is rolled into its parent Story by
     * {@link #effectiveEffort}, so returning Tasks as units too would count that work twice.
     */
    public List<WorkItem> deliverableUnits(EffortBearingLevel level, List<WorkItem> items) {
        if (level == EffortBearingLevel.TASK) {
            List<WorkItem> tasks = items.stream().filter(wi -> isTaskTier(wi.getResolvedType())).toList();
            // Defensive: an org pinned to TASK whose sprint happens to contain no tasks should
            // still show its story-tier work rather than an empty dashboard.
            return tasks.isEmpty() ? storyTier(items) : tasks;
        }
        return storyTier(items);
    }

    /** Resolves the level from {@code items} and returns their deliverable units. */
    public List<WorkItem> deliverableUnits(List<WorkItem> items) {
        return deliverableUnits(resolve(null, items), items);
    }

    /**
     * The single velocity definition over a scope (a sprint, a project window, a member's items):
     * level inferred from {@code scopeItems}, effective effort resolved against them.
     */
    public EffortSummary summarize(List<WorkItem> scopeItems) {
        List<WorkItem> units = deliverableUnits(scopeItems);
        List<WorkItem> done = units.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .toList();
        EffortScope scope = scope(scopeItems);
        return new EffortSummary(units, done, scope.totalEffort(done), scope.totalEffort(units), scope);
    }

    /** Children index built once for {@code allItems}; reuse it for every unit of the scope. */
    public EffortScope scope(List<WorkItem> allItems) {
        return new EffortScope(childrenIndex(allItems));
    }

    /**
     * The effort to attribute to {@code item}, given the whole item set for context.
     *
     * @return the item's own effort when set; otherwise the sum of its descendants' effort;
     *         {@code null} when neither exists (so "no data" stays distinct from a real zero)
     */
    public Double effectiveEffort(WorkItem item, List<WorkItem> allItems) {
        return scope(allItems).effectiveEffort(item);
    }

    /** Same rule as {@link #effectiveEffort}, applied to complexity. */
    public Double effectiveComplexity(WorkItem item, List<WorkItem> allItems) {
        return scope(allItems).effectiveComplexity(item);
    }

    /** Total effective effort across a set of deliverable units, skipping units with no data. */
    public Double totalEffort(List<WorkItem> units, List<WorkItem> allItems) {
        return scope(allItems).totalEffort(units);
    }

    public boolean isStoryTier(WorkItem.ResolvedType resolvedType) {
        return resolvedType == WorkItem.ResolvedType.STORY
                || resolvedType == WorkItem.ResolvedType.FEATURE
                || resolvedType == WorkItem.ResolvedType.BUG;
    }

    public boolean isTaskTier(WorkItem.ResolvedType resolvedType) {
        return resolvedType == WorkItem.ResolvedType.TASK
                || resolvedType == WorkItem.ResolvedType.SUB_TASK;
    }

    private List<WorkItem> storyTier(List<WorkItem> items) {
        return items.stream().filter(wi -> isStoryTier(wi.getResolvedType())).toList();
    }

    private static Map<String, List<WorkItem>> childrenIndex(List<WorkItem> items) {
        Map<String, List<WorkItem>> index = new HashMap<>();
        for (WorkItem item : items) {
            if (item.getParentExternalId() == null) continue;
            index.computeIfAbsent(item.getParentExternalId(), k -> new ArrayList<>()).add(item);
        }
        return index;
    }

    /**
     * Velocity of a scope.
     *
     * @param units     deliverable units of the scope
     * @param doneUnits units in {@code DONE}
     * @param delivered effective effort of {@code doneUnits}; {@code null} when none carries effort
     * @param planned   effective effort of {@code units}; {@code null} when none carries effort
     * @param scope     the children index of the scope, for per-unit effort
     */
    public record EffortSummary(List<WorkItem> units, List<WorkItem> doneUnits,
                                Double delivered, Double planned, EffortScope scope) {

        public double deliveredOrZero() {
            return delivered == null ? 0d : delivered;
        }

        public double plannedOrZero() {
            return planned == null ? 0d : planned;
        }
    }

    /** Effective effort/complexity against one pre-built children index. */
    public static final class EffortScope {

        private final Map<String, List<WorkItem>> childrenIndex;

        private EffortScope(Map<String, List<WorkItem>> childrenIndex) {
            this.childrenIndex = childrenIndex;
        }

        public Double effectiveEffort(WorkItem item) {
            if (item.getEffort() != null) return item.getEffort();
            return sumDescendants(item, true);
        }

        public Double effectiveComplexity(WorkItem item) {
            if (item.getComplexity() != null) return item.getComplexity();
            return sumDescendants(item, false);
        }

        public Double totalEffort(List<WorkItem> units) {
            double sum = 0;
            int count = 0;
            for (WorkItem unit : units) {
                Double effort = effectiveEffort(unit);
                if (effort != null) {
                    sum += effort;
                    count++;
                }
            }
            return count == 0 ? null : sum;
        }

        private Double sumDescendants(WorkItem item, boolean effort) {
            double sum = 0;
            int count = 0;
            for (WorkItem descendant : descendantsOf(item)) {
                Double value = effort ? descendant.getEffort() : descendant.getComplexity();
                if (value != null) {
                    sum += value;
                    count++;
                }
            }
            return count == 0 ? null : sum;
        }

        private List<WorkItem> descendantsOf(WorkItem item) {
            List<WorkItem> out = new ArrayList<>();
            if (item.getExternalId() == null) return out;
            Set<String> visited = new HashSet<>();
            visited.add(item.getExternalId());
            Deque<WorkItem> stack = new ArrayDeque<>(childrenIndex.getOrDefault(item.getExternalId(), List.of()));
            while (!stack.isEmpty()) {
                WorkItem current = stack.pop();
                if (current.getExternalId() != null && !visited.add(current.getExternalId())) continue;
                out.add(current);
                stack.addAll(childrenIndex.getOrDefault(current.getExternalId(), List.of()));
            }
            return out;
        }
    }
}

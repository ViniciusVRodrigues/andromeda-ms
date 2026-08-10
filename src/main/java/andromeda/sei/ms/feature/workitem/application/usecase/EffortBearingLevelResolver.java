package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.EffortBearingLevel;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * The effort to attribute to {@code item}, given the whole item set for context.
     *
     * @return the item's own effort when set; otherwise the sum of its descendants' effort;
     *         {@code null} when neither exists (so "no data" stays distinct from a real zero)
     */
    public Double effectiveEffort(WorkItem item, List<WorkItem> allItems) {
        if (item.getEffort() != null) return item.getEffort();
        return sumDescendantEffort(item, childrenIndex(allItems));
    }

    /** Same rule as {@link #effectiveEffort}, applied to complexity. */
    public Double effectiveComplexity(WorkItem item, List<WorkItem> allItems) {
        if (item.getComplexity() != null) return item.getComplexity();
        Map<String, List<WorkItem>> index = childrenIndex(allItems);
        double sum = 0;
        int count = 0;
        for (WorkItem descendant : descendantsOf(item, index)) {
            if (descendant.getComplexity() != null) {
                sum += descendant.getComplexity();
                count++;
            }
        }
        return count == 0 ? null : sum;
    }

    /** Total effective effort across a set of deliverable units, skipping units with no data. */
    public Double totalEffort(List<WorkItem> units, List<WorkItem> allItems) {
        double sum = 0;
        int count = 0;
        for (WorkItem unit : units) {
            Double effort = effectiveEffort(unit, allItems);
            if (effort != null) {
                sum += effort;
                count++;
            }
        }
        return count == 0 ? null : sum;
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

    private Double sumDescendantEffort(WorkItem item, Map<String, List<WorkItem>> index) {
        double sum = 0;
        int count = 0;
        for (WorkItem descendant : descendantsOf(item, index)) {
            if (descendant.getEffort() != null) {
                sum += descendant.getEffort();
                count++;
            }
        }
        return count == 0 ? null : sum;
    }

    private List<WorkItem> descendantsOf(WorkItem item, Map<String, List<WorkItem>> index) {
        List<WorkItem> out = new ArrayList<>();
        if (item.getExternalId() == null) return out;
        java.util.Set<String> visited = new java.util.HashSet<>();
        visited.add(item.getExternalId());
        java.util.Deque<WorkItem> stack = new java.util.ArrayDeque<>(
                index.getOrDefault(item.getExternalId(), List.of()));
        while (!stack.isEmpty()) {
            WorkItem current = stack.pop();
            if (current.getExternalId() != null && !visited.add(current.getExternalId())) continue;
            out.add(current);
            stack.addAll(index.getOrDefault(current.getExternalId(), List.of()));
        }
        return out;
    }

    private Map<String, List<WorkItem>> childrenIndex(List<WorkItem> items) {
        Map<String, List<WorkItem>> index = new HashMap<>();
        for (WorkItem item : items) {
            if (item.getParentExternalId() == null) continue;
            index.computeIfAbsent(item.getParentExternalId(), k -> new ArrayList<>()).add(item);
        }
        return index;
    }
}

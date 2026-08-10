package andromeda.sei.ms.feature.workitem.domain;

/**
 * Read-time hierarchy-derived facts about a single work item: whether it has valid children, and
 * the complexity/effort rolled up from its descendants.
 *
 * <p>These are never persisted. Azure DevOps exposes a different scheduling field per work item
 * type (User Story carries StoryPoints, Task carries CompletedWork, Feature/Epic carry Effort),
 * and teams routinely fill only some levels — a project can legitimately have all of its
 * complexity on User Stories and all of its effort on their child Tasks, leaving Epics and
 * Features with no value of their own. Summing descendants is what makes those upper levels
 * readable without inventing data.
 *
 * <p>{@code rolledUpComplexity}/{@code rolledUpEffort} are the sum over ALL descendants
 * (recursively), excluding the item's own value. They are {@code null} when no descendant carries
 * a value, so "no data anywhere below" stays distinguishable from a genuine zero. An item's own
 * value always takes precedence in display; the rolled-up figure is complementary information the
 * UI can show as "soma dos filhos".
 */
public record WorkItemHierarchyMetrics(
        boolean hasChildren,
        Double rolledUpComplexity,
        Double rolledUpEffort,
        int descendantCount
) {
    public static final WorkItemHierarchyMetrics EMPTY =
            new WorkItemHierarchyMetrics(false, null, null, 0);
}

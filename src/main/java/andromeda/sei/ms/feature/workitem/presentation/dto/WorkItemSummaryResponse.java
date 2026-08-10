package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;

/**
 * @param hasChildren          whether this item has children that are valid under the project's
 *                             declared hierarchy — lets the UI hide the expand toggle on leaves
 *                             instead of discovering emptiness only after a click.
 * @param rolledUpComplexity   complexity summed over all descendants, {@code null} when no
 *                             descendant carries a value. Complementary to {@code complexity};
 *                             the item's own value always wins in display.
 * @param rolledUpEffort       effort summed over all descendants, same semantics.
 * @param descendantCount      how many descendants contributed to the rollup, so the UI can label
 *                             it honestly ("soma de N itens") rather than implying an own value.
 */
public record WorkItemSummaryResponse(
        String id,
        String externalId,
        String title,
        String rawType,
        WorkItem.ResolvedType resolvedType,
        String rawStatus,
        WorkItem.ResolvedStatus resolvedStatus,
        String sprintId,
        String parentExternalId,
        Double complexity,
        String complexitySource,
        Double effort,
        String effortSource,
        boolean hasChildren,
        Double rolledUpComplexity,
        Double rolledUpEffort,
        int descendantCount
) {
    public static WorkItemSummaryResponse from(WorkItem item) {
        return from(item, WorkItemHierarchyMetrics.EMPTY);
    }

    public static WorkItemSummaryResponse from(WorkItem item, WorkItemHierarchyMetrics metrics) {
        WorkItemHierarchyMetrics effective = metrics == null ? WorkItemHierarchyMetrics.EMPTY : metrics;
        return new WorkItemSummaryResponse(
                item.getId(),
                item.getExternalId(),
                item.getTitle(),
                item.getRawType(),
                item.getResolvedType(),
                item.getRawStatus(),
                item.getResolvedStatus(),
                item.getSprintId(),
                item.getParentExternalId(),
                item.getComplexity(),
                item.getComplexitySource() != null ? item.getComplexitySource().name() : null,
                item.getEffort(),
                item.getEffortSource() != null ? item.getEffortSource().name() : null,
                effective.hasChildren(),
                effective.rolledUpComplexity(),
                effective.rolledUpEffort(),
                effective.descendantCount());
    }
}

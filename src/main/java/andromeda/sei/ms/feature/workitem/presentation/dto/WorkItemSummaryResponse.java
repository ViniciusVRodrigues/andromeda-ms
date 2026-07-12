package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

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
        String effortSource
) {
    public static WorkItemSummaryResponse from(WorkItem item) {
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
                item.getEffortSource() != null ? item.getEffortSource().name() : null);
    }
}

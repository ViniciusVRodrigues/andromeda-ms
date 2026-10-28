package andromeda.sei.ms.feature.workitem.application.dto;

import andromeda.sei.ms.feature.workitem.domain.BacklogItemInfo;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;

import java.util.List;
import java.util.Map;

/**
 * @param metricsByWorkItemId hierarchy metrics for the returned children, so a child that is
 *                            itself a parent still reports {@code hasChildren} and its rollups —
 *                            without it the UI could only hide expand toggles at the first level.
 */
public record WorkItemChildrenOutput(
        List<WorkItem> children,
        boolean truncated,
        Map<String, WorkItemHierarchyMetrics> metricsByWorkItemId,
        Map<String, BacklogItemInfo> infoByWorkItemId
) {
    public WorkItemChildrenOutput(List<WorkItem> children, boolean truncated) {
        this(children, truncated, Map.of(), Map.of());
    }

    public WorkItemChildrenOutput(List<WorkItem> children, boolean truncated,
                                  Map<String, WorkItemHierarchyMetrics> metricsByWorkItemId) {
        this(children, truncated, metricsByWorkItemId, Map.of());
    }

    public BacklogItemInfo infoFor(String workItemId) {
        return infoByWorkItemId.getOrDefault(workItemId, BacklogItemInfo.NONE);
    }

    public WorkItemHierarchyMetrics metricsFor(String workItemId) {
        return metricsByWorkItemId.getOrDefault(workItemId, WorkItemHierarchyMetrics.EMPTY);
    }
}

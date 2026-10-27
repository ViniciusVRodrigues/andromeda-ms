package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;
import java.util.Map;

/**
 * Plain-Java page result for hierarchy-paginated work item queries, kept dependency-free
 * (no Spring Data types) so it can live in the domain layer per Clean Architecture rules.
 */
public class WorkItemPageResult {

    private final List<WorkItem> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final Map<String, WorkItemHierarchyMetrics> metricsByWorkItemId;
    private final Map<String, BacklogItemInfo> infoByWorkItemId;
    private final List<BacklogLevel> levels;

    public WorkItemPageResult(List<WorkItem> content, int page, int size, long totalElements) {
        this(content, page, size, totalElements, Map.of());
    }

    public WorkItemPageResult(List<WorkItem> content, int page, int size, long totalElements,
                               Map<String, WorkItemHierarchyMetrics> metricsByWorkItemId) {
        this(content, page, size, totalElements, metricsByWorkItemId, Map.of(), List.of());
    }

    public WorkItemPageResult(List<WorkItem> content, int page, int size, long totalElements,
                               Map<String, WorkItemHierarchyMetrics> metricsByWorkItemId,
                               Map<String, BacklogItemInfo> infoByWorkItemId, List<BacklogLevel> levels) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.metricsByWorkItemId = metricsByWorkItemId == null ? Map.of() : metricsByWorkItemId;
        this.infoByWorkItemId = infoByWorkItemId == null ? Map.of() : infoByWorkItemId;
        this.levels = levels == null ? List.of() : levels;
    }

    public List<WorkItem> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }

    /**
     * Hierarchy-derived metrics (hasChildren + rolled-up values) for the items on this page.
     * Empty when the query didn't compute them; callers should fall back to
     * {@link WorkItemHierarchyMetrics#EMPTY} for a missing id rather than assume presence.
     */
    public Map<String, WorkItemHierarchyMetrics> getMetricsByWorkItemId() { return metricsByWorkItemId; }

    /** Levels of the whole root result (all pages); empty when not computed. */
    public List<BacklogLevel> getLevels() { return levels; }

    public BacklogItemInfo infoFor(String workItemId) {
        return infoByWorkItemId.getOrDefault(workItemId, BacklogItemInfo.NONE);
    }

    public WorkItemHierarchyMetrics metricsFor(String workItemId) {
        return metricsByWorkItemId.getOrDefault(workItemId, WorkItemHierarchyMetrics.EMPTY);
    }
}

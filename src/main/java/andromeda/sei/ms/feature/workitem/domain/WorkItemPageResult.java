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

    public WorkItemPageResult(List<WorkItem> content, int page, int size, long totalElements) {
        this(content, page, size, totalElements, Map.of());
    }

    public WorkItemPageResult(List<WorkItem> content, int page, int size, long totalElements,
                               Map<String, WorkItemHierarchyMetrics> metricsByWorkItemId) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.metricsByWorkItemId = metricsByWorkItemId == null ? Map.of() : metricsByWorkItemId;
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

    public WorkItemHierarchyMetrics metricsFor(String workItemId) {
        return metricsByWorkItemId.getOrDefault(workItemId, WorkItemHierarchyMetrics.EMPTY);
    }
}

package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;

/**
 * Plain-Java page result for hierarchy-paginated work item queries, kept dependency-free
 * (no Spring Data types) so it can live in the domain layer per Clean Architecture rules.
 */
public class WorkItemPageResult {

    private final List<WorkItem> content;
    private final int page;
    private final int size;
    private final long totalElements;

    public WorkItemPageResult(List<WorkItem> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<WorkItem> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
}

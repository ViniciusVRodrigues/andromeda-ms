package andromeda.sei.ms.feature.azure.domain;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

public class StatusMapping {

    private String workItemType;
    private String rawStatus;
    private WorkItem.ResolvedStatus resolvedStatus;

    /**
     * @param workItemType the raw Azure work item type this status mapping applies to.
     *                     {@code null} is the legacy-compatibility sentinel meaning "applies to
     *                     all work item types" — preserves the behavior of records persisted
     *                     before status mapping became per-work-item-type (see design.md).
     */
    public StatusMapping(String workItemType, String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {
        this.workItemType = workItemType;
        this.rawStatus = rawStatus;
        this.resolvedStatus = resolvedStatus;
    }

    public String getWorkItemType() { return workItemType; }
    public void setWorkItemType(String workItemType) { this.workItemType = workItemType; }

    public String getRawStatus() { return rawStatus; }
    public void setRawStatus(String rawStatus) { this.rawStatus = rawStatus; }

    public WorkItem.ResolvedStatus getResolvedStatus() { return resolvedStatus; }
    public void setResolvedStatus(WorkItem.ResolvedStatus resolvedStatus) { this.resolvedStatus = resolvedStatus; }
}

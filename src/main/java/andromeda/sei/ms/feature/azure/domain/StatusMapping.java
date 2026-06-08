package andromeda.sei.ms.feature.azure.domain;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

public class StatusMapping {

    private String rawStatus;
    private WorkItem.ResolvedStatus resolvedStatus;

    public StatusMapping(String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {
        this.rawStatus = rawStatus;
        this.resolvedStatus = resolvedStatus;
    }

    public String getRawStatus() { return rawStatus; }
    public void setRawStatus(String rawStatus) { this.rawStatus = rawStatus; }

    public WorkItem.ResolvedStatus getResolvedStatus() { return resolvedStatus; }
    public void setResolvedStatus(WorkItem.ResolvedStatus resolvedStatus) { this.resolvedStatus = resolvedStatus; }
}

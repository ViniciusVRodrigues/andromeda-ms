package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;

public class WorkItemTransition {

    private String id;
    private String organizationId;
    private String workItemId;
    private String fromRawStatus;
    private String toRawStatus;
    private WorkItem.ResolvedStatus toResolvedStatus;
    private Instant occurredAt;
    private Instant recordedAt;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkItemTransition(String id, String organizationId, String workItemId,
                              String fromRawStatus, String toRawStatus, WorkItem.ResolvedStatus toResolvedStatus,
                              Instant occurredAt, Instant recordedAt,
                              boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.workItemId = workItemId;
        this.fromRawStatus = fromRawStatus;
        this.toRawStatus = toRawStatus;
        this.toResolvedStatus = toResolvedStatus;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }

    public String getFromRawStatus() { return fromRawStatus; }
    public void setFromRawStatus(String fromRawStatus) { this.fromRawStatus = fromRawStatus; }

    public String getToRawStatus() { return toRawStatus; }
    public void setToRawStatus(String toRawStatus) { this.toRawStatus = toRawStatus; }

    public WorkItem.ResolvedStatus getToResolvedStatus() { return toResolvedStatus; }
    public void setToResolvedStatus(WorkItem.ResolvedStatus toResolvedStatus) { this.toResolvedStatus = toResolvedStatus; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

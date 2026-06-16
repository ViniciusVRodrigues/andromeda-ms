package andromeda.sei.ms.feature.allocation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AiUsageAllocation {

    public enum Status { UNALLOCATED, PENDING_VALIDATION, VALIDATED }
    public enum AssignedBy { AUTO, MANUAL }

    private String id;
    private String organizationId;
    private String userId;
    private LocalDate day;
    private List<String> aiUsageIds;
    private List<AllocationSplit> splits;
    private Status status;
    private AssignedBy assignedBy;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public AiUsageAllocation(String id, String organizationId, String userId, LocalDate day,
                              List<String> aiUsageIds, List<AllocationSplit> splits,
                              Status status, AssignedBy assignedBy,
                              boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.day = day;
        this.aiUsageIds = aiUsageIds;
        this.splits = splits;
        this.status = status;
        this.assignedBy = assignedBy;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }

    public List<String> getAiUsageIds() { return aiUsageIds; }
    public void setAiUsageIds(List<String> aiUsageIds) { this.aiUsageIds = aiUsageIds; }

    public List<AllocationSplit> getSplits() { return splits; }
    public void setSplits(List<AllocationSplit> splits) { this.splits = splits; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public AssignedBy getAssignedBy() { return assignedBy; }
    public void setAssignedBy(AssignedBy assignedBy) { this.assignedBy = assignedBy; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

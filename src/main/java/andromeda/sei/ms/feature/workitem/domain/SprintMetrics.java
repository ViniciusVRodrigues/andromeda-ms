package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;

public class SprintMetrics {

    private String id;
    private String organizationId;
    private String projectId;
    private String sprintId;
    private WorkItem.Source source;
    private Double velocity;
    private Double avgCycleTime;
    private Double avgLeadTime;
    private Integer completedItemCount;
    private Integer totalItemCount;
    private Integer itemsWithPoints;
    private Instant calculatedAt;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public SprintMetrics(String id, String organizationId, String projectId, String sprintId,
                         WorkItem.Source source, Double velocity, Double avgCycleTime, Double avgLeadTime,
                         Integer completedItemCount, Integer totalItemCount, Integer itemsWithPoints,
                         Instant calculatedAt, boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.sprintId = sprintId;
        this.source = source;
        this.velocity = velocity;
        this.avgCycleTime = avgCycleTime;
        this.avgLeadTime = avgLeadTime;
        this.completedItemCount = completedItemCount;
        this.totalItemCount = totalItemCount;
        this.itemsWithPoints = itemsWithPoints;
        this.calculatedAt = calculatedAt;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getSprintId() { return sprintId; }
    public void setSprintId(String sprintId) { this.sprintId = sprintId; }

    public WorkItem.Source getSource() { return source; }
    public void setSource(WorkItem.Source source) { this.source = source; }

    public Double getVelocity() { return velocity; }
    public void setVelocity(Double velocity) { this.velocity = velocity; }

    public Double getAvgCycleTime() { return avgCycleTime; }
    public void setAvgCycleTime(Double avgCycleTime) { this.avgCycleTime = avgCycleTime; }

    public Double getAvgLeadTime() { return avgLeadTime; }
    public void setAvgLeadTime(Double avgLeadTime) { this.avgLeadTime = avgLeadTime; }

    public Integer getCompletedItemCount() { return completedItemCount; }
    public void setCompletedItemCount(Integer completedItemCount) { this.completedItemCount = completedItemCount; }

    public Integer getTotalItemCount() { return totalItemCount; }
    public void setTotalItemCount(Integer totalItemCount) { this.totalItemCount = totalItemCount; }

    public Integer getItemsWithPoints() { return itemsWithPoints; }
    public void setItemsWithPoints(Integer itemsWithPoints) { this.itemsWithPoints = itemsWithPoints; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

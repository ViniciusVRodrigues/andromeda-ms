package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;

public class Sprint {

    public enum TimeFrame { PAST, CURRENT, FUTURE }

    private String id;
    private String organizationId;
    private String projectId;
    private WorkItem.Source source;
    private String externalId;
    private String teamId;
    private String teamName;
    private String name;
    private String path;
    private Instant startDate;
    private Instant endDate;
    private TimeFrame timeFrame;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Sprint(String id, String organizationId, String projectId, WorkItem.Source source,
                  String externalId, String teamId, String teamName, String name, String path,
                  Instant startDate, Instant endDate, TimeFrame timeFrame,
                  boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.source = source;
        this.externalId = externalId;
        this.teamId = teamId;
        this.teamName = teamName;
        this.name = name;
        this.path = path;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timeFrame = timeFrame;
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

    public WorkItem.Source getSource() { return source; }
    public void setSource(WorkItem.Source source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public TimeFrame getTimeFrame() { return timeFrame; }
    public void setTimeFrame(TimeFrame timeFrame) { this.timeFrame = timeFrame; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

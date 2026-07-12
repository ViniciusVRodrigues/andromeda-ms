package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;
import java.util.List;

public class WorkItem {

    public enum Source { AZURE_DEVOPS, JIRA }

    public enum ResolvedType { STORY, BUG, TASK, EPIC, SUB_TASK }

    public enum ResolvedStatus { TODO, IN_PROGRESS, DONE }

    private String id;
    private String organizationId;
    private String projectId;
    private Source source;
    private String externalId;
    private Instant externalUpdatedAt;
    private String title;
    private String rawType;
    private ResolvedType resolvedType;
    private String rawStatus;
    private ResolvedStatus resolvedStatus;
    private String priority;
    private Double storyPoints;
    private List<String> labels;
    private String sprintId;
    private String assigneeExternalId;
    private String assigneeUserId;
    private String assigneeEmail;
    private String parentExternalId;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Double cycleTimeHours;
    private Double leadTimeHours;
    private Double tokensPerSp;
    private boolean active;
    private Long version;
    private Instant updatedAt;
    private Double complexity;
    private FieldValueSource complexitySource;
    private String complexityOverriddenBy;
    private Instant complexityOverriddenAt;
    private Double effort;
    private FieldValueSource effortSource;
    private String effortOverriddenBy;
    private Instant effortOverriddenAt;

    public WorkItem(String id, String organizationId, String projectId, Source source,
                    String externalId, Instant externalUpdatedAt, String title,
                    String rawType, ResolvedType resolvedType, String rawStatus, ResolvedStatus resolvedStatus,
                    String priority, Double storyPoints, List<String> labels,
                    String sprintId, String assigneeExternalId, String assigneeUserId, String assigneeEmail,
                    String parentExternalId, Instant createdAt, Instant startedAt, Instant completedAt,
                    Double cycleTimeHours, Double leadTimeHours, Double tokensPerSp,
                    boolean active, Long version, Instant updatedAt,
                    Double complexity, FieldValueSource complexitySource,
                    String complexityOverriddenBy, Instant complexityOverriddenAt,
                    Double effort, FieldValueSource effortSource,
                    String effortOverriddenBy, Instant effortOverriddenAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.source = source;
        this.externalId = externalId;
        this.externalUpdatedAt = externalUpdatedAt;
        this.title = title;
        this.rawType = rawType;
        this.resolvedType = resolvedType;
        this.rawStatus = rawStatus;
        this.resolvedStatus = resolvedStatus;
        this.priority = priority;
        this.storyPoints = storyPoints;
        this.labels = labels;
        this.sprintId = sprintId;
        this.assigneeExternalId = assigneeExternalId;
        this.assigneeUserId = assigneeUserId;
        this.assigneeEmail = assigneeEmail;
        this.parentExternalId = parentExternalId;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.cycleTimeHours = cycleTimeHours;
        this.leadTimeHours = leadTimeHours;
        this.tokensPerSp = tokensPerSp;
        this.active = active;
        this.version = version;
        this.updatedAt = updatedAt;
        this.complexity = complexity;
        this.complexitySource = complexitySource;
        this.complexityOverriddenBy = complexityOverriddenBy;
        this.complexityOverriddenAt = complexityOverriddenAt;
        this.effort = effort;
        this.effortSource = effortSource;
        this.effortOverriddenBy = effortOverriddenBy;
        this.effortOverriddenAt = effortOverriddenAt;
    }

    public void updateMetrics(Double cycleTimeHours, Double leadTimeHours) {
        this.cycleTimeHours = cycleTimeHours;
        this.leadTimeHours = leadTimeHours;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Instant getExternalUpdatedAt() { return externalUpdatedAt; }
    public void setExternalUpdatedAt(Instant externalUpdatedAt) { this.externalUpdatedAt = externalUpdatedAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRawType() { return rawType; }
    public void setRawType(String rawType) { this.rawType = rawType; }

    public ResolvedType getResolvedType() { return resolvedType; }
    public void setResolvedType(ResolvedType resolvedType) { this.resolvedType = resolvedType; }

    public String getRawStatus() { return rawStatus; }
    public void setRawStatus(String rawStatus) { this.rawStatus = rawStatus; }

    public ResolvedStatus getResolvedStatus() { return resolvedStatus; }
    public void setResolvedStatus(ResolvedStatus resolvedStatus) { this.resolvedStatus = resolvedStatus; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Double getStoryPoints() { return storyPoints; }
    public void setStoryPoints(Double storyPoints) { this.storyPoints = storyPoints; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public String getSprintId() { return sprintId; }
    public void setSprintId(String sprintId) { this.sprintId = sprintId; }

    public String getAssigneeExternalId() { return assigneeExternalId; }
    public void setAssigneeExternalId(String assigneeExternalId) { this.assigneeExternalId = assigneeExternalId; }

    public String getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(String assigneeUserId) { this.assigneeUserId = assigneeUserId; }

    public String getAssigneeEmail() { return assigneeEmail; }
    public void setAssigneeEmail(String assigneeEmail) { this.assigneeEmail = assigneeEmail; }

    public String getParentExternalId() { return parentExternalId; }
    public void setParentExternalId(String parentExternalId) { this.parentExternalId = parentExternalId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Double getCycleTimeHours() { return cycleTimeHours; }
    public void setCycleTimeHours(Double cycleTimeHours) { this.cycleTimeHours = cycleTimeHours; }

    public Double getLeadTimeHours() { return leadTimeHours; }
    public void setLeadTimeHours(Double leadTimeHours) { this.leadTimeHours = leadTimeHours; }

    public Double getTokensPerSp() { return tokensPerSp; }
    public void setTokensPerSp(Double tokensPerSp) { this.tokensPerSp = tokensPerSp; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Double getComplexity() { return complexity; }
    public void setComplexity(Double complexity) { this.complexity = complexity; }

    public FieldValueSource getComplexitySource() { return complexitySource; }
    public void setComplexitySource(FieldValueSource complexitySource) { this.complexitySource = complexitySource; }

    public String getComplexityOverriddenBy() { return complexityOverriddenBy; }
    public void setComplexityOverriddenBy(String complexityOverriddenBy) { this.complexityOverriddenBy = complexityOverriddenBy; }

    public Instant getComplexityOverriddenAt() { return complexityOverriddenAt; }
    public void setComplexityOverriddenAt(Instant complexityOverriddenAt) { this.complexityOverriddenAt = complexityOverriddenAt; }

    public Double getEffort() { return effort; }
    public void setEffort(Double effort) { this.effort = effort; }

    public FieldValueSource getEffortSource() { return effortSource; }
    public void setEffortSource(FieldValueSource effortSource) { this.effortSource = effortSource; }

    public String getEffortOverriddenBy() { return effortOverriddenBy; }
    public void setEffortOverriddenBy(String effortOverriddenBy) { this.effortOverriddenBy = effortOverriddenBy; }

    public Instant getEffortOverriddenAt() { return effortOverriddenAt; }
    public void setEffortOverriddenAt(Instant effortOverriddenAt) { this.effortOverriddenAt = effortOverriddenAt; }
}

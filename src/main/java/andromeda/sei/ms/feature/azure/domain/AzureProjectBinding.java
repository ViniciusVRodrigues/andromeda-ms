package andromeda.sei.ms.feature.azure.domain;

import java.time.Instant;

public class AzureProjectBinding {

    private String id;
    private String organizationId;
    private String projectId;
    private String azureProjectId;
    private String azureProjectName;
    private boolean syncEnabled;
    private Instant lastSyncAt;
    private WorkflowConfig workflowConfig;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public AzureProjectBinding(String id, String organizationId, String projectId,
                                String azureProjectId, String azureProjectName,
                                boolean syncEnabled, Instant lastSyncAt,
                                WorkflowConfig workflowConfig,
                                boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.azureProjectId = azureProjectId;
        this.azureProjectName = azureProjectName;
        this.syncEnabled = syncEnabled;
        this.lastSyncAt = lastSyncAt;
        this.workflowConfig = workflowConfig;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAzureProjectId() { return azureProjectId; }
    public void setAzureProjectId(String azureProjectId) { this.azureProjectId = azureProjectId; }

    public String getAzureProjectName() { return azureProjectName; }
    public void setAzureProjectName(String azureProjectName) { this.azureProjectName = azureProjectName; }

    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public WorkflowConfig getWorkflowConfig() { return workflowConfig; }
    public void setWorkflowConfig(WorkflowConfig workflowConfig) { this.workflowConfig = workflowConfig; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package andromeda.sei.ms.feature.azure.domain;

import java.time.Instant;

/**
 * A persisted snapshot of an Azure DevOps project belonging to a connected organization,
 * synced independently of any project binding. Queryable without a live Azure API round-trip.
 */
public class AzureOrgProject {

    private String id;
    private String organizationId;
    private String azureProjectId;
    private String azureProjectName;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public AzureOrgProject(String id, String organizationId, String azureProjectId, String azureProjectName,
                            boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.azureProjectId = azureProjectId;
        this.azureProjectName = azureProjectName;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getAzureProjectId() { return azureProjectId; }
    public void setAzureProjectId(String azureProjectId) { this.azureProjectId = azureProjectId; }

    public String getAzureProjectName() { return azureProjectName; }
    public void setAzureProjectName(String azureProjectName) { this.azureProjectName = azureProjectName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

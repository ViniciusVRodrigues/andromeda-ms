package andromeda.sei.ms.feature.azure.domain;

import java.time.Instant;

/**
 * A persisted snapshot of an Azure DevOps org member belonging to a connected organization,
 * synced independently of any project binding. Queryable without a live Azure API round-trip.
 */
public class AzureOrgUser {

    private String id;
    private String organizationId;
    private String azureId;
    private String displayName;
    private String azureEmail;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public AzureOrgUser(String id, String organizationId, String azureId, String displayName, String azureEmail,
                         boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.azureId = azureId;
        this.displayName = displayName;
        this.azureEmail = azureEmail;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getAzureId() { return azureId; }
    public void setAzureId(String azureId) { this.azureId = azureId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAzureEmail() { return azureEmail; }
    public void setAzureEmail(String azureEmail) { this.azureEmail = azureEmail; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

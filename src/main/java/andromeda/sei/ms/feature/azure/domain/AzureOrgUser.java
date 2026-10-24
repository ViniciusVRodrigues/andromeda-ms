package andromeda.sei.ms.feature.azure.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

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
    private String principalName;
    private Set<AzureOrgUserSource> sources;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public AzureOrgUser(String id, String organizationId, String azureId, String displayName, String azureEmail,
                         String principalName, Collection<AzureOrgUserSource> sources, boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.azureId = azureId;
        this.displayName = displayName;
        this.azureEmail = azureEmail;
        this.principalName = principalName;
        this.sources = sources == null || sources.isEmpty()
                ? EnumSet.noneOf(AzureOrgUserSource.class) : EnumSet.copyOf(sources);
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

    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String principalName) { this.principalName = principalName; }

    public Set<AzureOrgUserSource> getSources() { return sources; }

    /**
     * Merges an identity seen by {@code source} into this one. A source with precedence at least as
     * high as every source already recorded overwrites with its non-blank values; a lower one only
     * fills blank fields. {@code sources} always accumulates.
     */
    public void mergeFrom(String displayName, String email, String principalName, AzureOrgUserSource source) {
        int current = sources.stream().mapToInt(AzureOrgUserSource::precedence).max().orElse(0);
        boolean overwrite = source.precedence() >= current;
        this.displayName = pick(this.displayName, displayName, overwrite);
        this.azureEmail = pick(this.azureEmail, email, overwrite);
        this.principalName = pick(this.principalName, principalName, overwrite);
        sources.add(source);
    }

    private static String pick(String existing, String observed, boolean overwrite) {
        if (observed == null || observed.isBlank()) return existing;
        if (existing == null || existing.isBlank() || overwrite) return observed;
        return existing;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

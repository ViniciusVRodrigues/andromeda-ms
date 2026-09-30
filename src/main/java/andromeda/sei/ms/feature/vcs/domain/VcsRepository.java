package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;

/**
 * A repository of any provider, identified per organization by {@code (provider, externalRepoId)}
 * (design D2). Pull requests, commits and review events reference it only by its internal
 * {@link #getId() id}.
 *
 * <p>{@code externalRepoId} is {@code null} while the repository is pending its first sync — a
 * binding migrated from the old {@code owner/name} key whose repository was never fetched. The
 * first sync resolves it from {@link #getDisplayName()}.
 */
public class VcsRepository {

    private String id;
    private String organizationId;
    private VcsProvider provider;
    /** GitHub numeric id / Azure Repos GUID; null while pending first sync. */
    private String externalRepoId;
    /** Azure DevOps project GUID (AZURE_REPOS only). */
    private String externalProjectId;
    /** GitHub {@code owner/name}; Azure Repos repository name. */
    private String displayName;
    /** Branch into which merges count as delivered changes; null until the repository is synced. */
    private String defaultBranch;
    private Instant externalUpdatedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public VcsRepository(String id, String organizationId, VcsProvider provider, String externalRepoId,
                         String displayName) {
        this.id = id;
        this.organizationId = organizationId;
        this.provider = provider;
        this.externalRepoId = externalRepoId;
        this.displayName = displayName;
        this.active = true;
    }

    public boolean isPendingFirstSync() {
        return externalRepoId == null || externalRepoId.isBlank();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public VcsProvider getProvider() { return provider; }
    public void setProvider(VcsProvider provider) { this.provider = provider; }

    public String getExternalRepoId() { return externalRepoId; }
    public void setExternalRepoId(String externalRepoId) { this.externalRepoId = externalRepoId; }

    public String getExternalProjectId() { return externalProjectId; }
    public void setExternalProjectId(String externalProjectId) { this.externalProjectId = externalProjectId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public Instant getExternalUpdatedAt() { return externalUpdatedAt; }
    public void setExternalUpdatedAt(Instant externalUpdatedAt) { this.externalUpdatedAt = externalUpdatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

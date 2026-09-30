package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;

/**
 * Links an Andromeda project to a repository by its <strong>internal</strong> id. An active binding
 * of a repository exists for at most one project per organization.
 *
 * <p>{@code lastSyncAt} is the read watermark of the repository's sync: the instant the last
 * successful run <em>started</em>.
 */
public class VcsRepoBinding {

    private String id;
    private String organizationId;
    private String projectId;
    private String repositoryId;
    private VcsProvider provider;
    private boolean syncEnabled;
    private Instant lastSyncAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public VcsRepoBinding(String id, String organizationId, String projectId, String repositoryId,
                          VcsProvider provider, boolean syncEnabled) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.repositoryId = repositoryId;
        this.provider = provider;
        this.syncEnabled = syncEnabled;
        this.active = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public VcsProvider getProvider() { return provider; }
    public void setProvider(VcsProvider provider) { this.provider = provider; }

    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

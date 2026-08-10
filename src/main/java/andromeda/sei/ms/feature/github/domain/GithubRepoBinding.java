package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class GithubRepoBinding {

    private String id;
    private String organizationId;
    private String projectId;
    private String repoId;
    private boolean syncEnabled;
    private Instant lastSyncAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public GithubRepoBinding(String id, String organizationId, String projectId, String repoId,
                              boolean syncEnabled, Instant lastSyncAt,
                              boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.repoId = repoId;
        this.syncEnabled = syncEnabled;
        this.lastSyncAt = lastSyncAt;
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

    public String getRepoId() { return repoId; }
    public void setRepoId(String repoId) { this.repoId = repoId; }

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

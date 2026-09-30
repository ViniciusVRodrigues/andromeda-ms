package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;

/** A commit of a repository, keyed by {@code (organizationId, repositoryId, sha)}. */
public class VcsCommit {

    private String id;
    private String organizationId;
    private String repositoryId;
    private VcsProvider provider;
    private String sha;
    private String message;
    private String authorLogin;
    private String authorUserId;
    private Instant authoredAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public VcsCommit(String id, String organizationId, String repositoryId, VcsProvider provider, String sha) {
        this.id = id;
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.provider = provider;
        this.sha = sha;
        this.active = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public VcsProvider getProvider() { return provider; }
    public void setProvider(VcsProvider provider) { this.provider = provider; }

    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAuthorLogin() { return authorLogin; }
    public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }

    public String getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(String authorUserId) { this.authorUserId = authorUserId; }

    public Instant getAuthoredAt() { return authoredAt; }
    public void setAuthoredAt(Instant authoredAt) { this.authoredAt = authoredAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

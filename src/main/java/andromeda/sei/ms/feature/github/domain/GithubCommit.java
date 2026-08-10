package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class GithubCommit {

    private String id;
    private String organizationId;
    private String repoId;
    private String sha;
    private String message;
    private String authorGithubLogin;
    private String authorUserId;
    private Instant authoredAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public GithubCommit(String id, String organizationId, String repoId, String sha, String message,
                         String authorGithubLogin, String authorUserId, Instant authoredAt,
                         boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.repoId = repoId;
        this.sha = sha;
        this.message = message;
        this.authorGithubLogin = authorGithubLogin;
        this.authorUserId = authorUserId;
        this.authoredAt = authoredAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getRepoId() { return repoId; }
    public void setRepoId(String repoId) { this.repoId = repoId; }

    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAuthorGithubLogin() { return authorGithubLogin; }
    public void setAuthorGithubLogin(String authorGithubLogin) { this.authorGithubLogin = authorGithubLogin; }

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

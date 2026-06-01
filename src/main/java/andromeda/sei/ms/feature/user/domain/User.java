package andromeda.sei.ms.feature.user.domain;

import java.time.Instant;

public class User {

    private String id;
    private String organizationId;
    private String email;
    private String passwordHash;
    private String name;
    private String roleId;
    private String githubId;
    private String inviteToken;
    private boolean confirmed;
    private boolean active;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public User(String id, String organizationId, String email, String passwordHash,
                String name, String roleId, String githubId, String inviteToken,
                boolean confirmed, boolean active,
                long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.roleId = roleId;
        this.githubId = githubId;
        this.inviteToken = inviteToken;
        this.confirmed = confirmed;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getGithubId() { return githubId; }
    public void setGithubId(String githubId) { this.githubId = githubId; }

    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

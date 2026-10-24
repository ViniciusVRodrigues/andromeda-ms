package andromeda.sei.ms.feature.user.domain;

import andromeda.sei.ms.shared.domain.security.PermissionOverride;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class User {

    private String id;
    private String organizationId;
    private String email;
    private String passwordHash;
    private String name;
    private String roleId;
    private String seniority;
    private String githubId;
    private String azureId;
    private String azureEmail;
    /** Origem do vinculo Azure; null em usuarios sem vinculo. */
    private AzureIdentitySource azureIdentitySource;
    private Instant azureIdentityLinkedAt;
    private String inviteToken;
    private boolean confirmed;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    /** Last successful login, written only by UserRepository.recordLogin ($set); null until the first one. */
    private Instant lastLoginAt;
    /** Per-user overrides on top of the role preset (project-scoped-permissions D1/D2). */
    private List<PermissionOverride> permissionOverrides = new ArrayList<>();

    public User(String id, String organizationId, String email, String passwordHash,
                String name, String roleId, String seniority, String githubId,
                String azureId, String azureEmail,
                String inviteToken,
                boolean confirmed, boolean active,
                Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.roleId = roleId;
        this.seniority = seniority;
        this.githubId = githubId;
        this.azureId = azureId;
        this.azureEmail = azureEmail;
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

    public String getSeniority() { return seniority; }
    public void setSeniority(String seniority) { this.seniority = seniority; }

    public String getGithubId() { return githubId; }
    public void setGithubId(String githubId) { this.githubId = githubId; }

    public String getAzureId() { return azureId; }
    public void setAzureId(String azureId) { this.azureId = azureId; }

    public String getAzureEmail() { return azureEmail; }
    public void setAzureEmail(String azureEmail) { this.azureEmail = azureEmail; }

    public AzureIdentitySource getAzureIdentitySource() { return azureIdentitySource; }
    public void setAzureIdentitySource(AzureIdentitySource azureIdentitySource) { this.azureIdentitySource = azureIdentitySource; }

    public Instant getAzureIdentityLinkedAt() { return azureIdentityLinkedAt; }
    public void setAzureIdentityLinkedAt(Instant azureIdentityLinkedAt) { this.azureIdentityLinkedAt = azureIdentityLinkedAt; }

    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public List<PermissionOverride> getPermissionOverrides() { return permissionOverrides; }
    public void setPermissionOverrides(List<PermissionOverride> permissionOverrides) {
        this.permissionOverrides = permissionOverrides != null ? new ArrayList<>(permissionOverrides) : new ArrayList<>();
    }
}

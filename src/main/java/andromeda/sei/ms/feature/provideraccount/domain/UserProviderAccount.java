package andromeda.sei.ms.feature.provideraccount.domain;

import java.time.Instant;
import java.util.List;

public class UserProviderAccount {

    private String id;
    private String organizationId;
    private String userId;
    private Provider provider;
    private String externalUserId;
    private String externalLogin;
    private String externalEmail;
    private String accessTokenEncrypted;
    private String refreshTokenEncrypted;
    private Instant tokenExpiresAt;
    private List<String> scopes;
    private Instant linkedAt;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UserProviderAccount(String id, String organizationId, String userId, Provider provider,
                                String externalUserId, String externalLogin, String externalEmail,
                                String accessTokenEncrypted, String refreshTokenEncrypted,
                                Instant tokenExpiresAt, List<String> scopes, Instant linkedAt,
                                boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.provider = provider;
        this.externalUserId = externalUserId;
        this.externalLogin = externalLogin;
        this.externalEmail = externalEmail;
        this.accessTokenEncrypted = accessTokenEncrypted;
        this.refreshTokenEncrypted = refreshTokenEncrypted;
        this.tokenExpiresAt = tokenExpiresAt;
        this.scopes = scopes;
        this.linkedAt = linkedAt;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getExternalUserId() { return externalUserId; }
    public void setExternalUserId(String externalUserId) { this.externalUserId = externalUserId; }

    public String getExternalLogin() { return externalLogin; }
    public void setExternalLogin(String externalLogin) { this.externalLogin = externalLogin; }

    public String getExternalEmail() { return externalEmail; }
    public void setExternalEmail(String externalEmail) { this.externalEmail = externalEmail; }

    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public void setAccessTokenEncrypted(String accessTokenEncrypted) { this.accessTokenEncrypted = accessTokenEncrypted; }

    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) { this.refreshTokenEncrypted = refreshTokenEncrypted; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

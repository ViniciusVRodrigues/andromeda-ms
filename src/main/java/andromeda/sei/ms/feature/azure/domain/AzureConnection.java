package andromeda.sei.ms.feature.azure.domain;

import java.time.Instant;

public class AzureConnection {

    private String id;
    private String organizationId;
    private String azureOrgUrl;
    private String accessToken;
    private String refreshToken;
    private Instant tokenExpiresAt;
    private String authorizedByUserId;
    private AzureConnectionStatus status;
    private Instant lastSuccessAt;
    private int consecutiveFailures;
    private String lastErrorCode;
    private String lastErrorMessage;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private AzureAuthMethod authMethod;
    private String personalAccessToken;
    /**
     * Whether the credential can read Git repositories (Code (Read) scope): {@code null} while
     * unknown (never checked, or the check failed for another reason). Boards never depends on it
     * (azure-repos-provider D6).
     */
    private Boolean reposAccess;
    /** Last completed org-level sync (directory + teams); null until the first one. */
    private Instant orgLevelSyncedAt;

    public AzureConnection(String id, String organizationId, String azureOrgUrl,
                            String accessToken, String refreshToken, Instant tokenExpiresAt,
                            String authorizedByUserId, AzureConnectionStatus status,
                            Instant lastSuccessAt, int consecutiveFailures,
                            String lastErrorCode, String lastErrorMessage,
                            boolean active, Instant createdAt, Instant updatedAt, Long version,
                            AzureAuthMethod authMethod, String personalAccessToken) {
        this.id = id;
        this.organizationId = organizationId;
        this.azureOrgUrl = azureOrgUrl;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.authorizedByUserId = authorizedByUserId;
        this.status = status;
        this.lastSuccessAt = lastSuccessAt;
        this.consecutiveFailures = consecutiveFailures;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.authMethod = authMethod;
        this.personalAccessToken = personalAccessToken;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getAzureOrgUrl() { return azureOrgUrl; }
    public void setAzureOrgUrl(String azureOrgUrl) { this.azureOrgUrl = azureOrgUrl; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public String getAuthorizedByUserId() { return authorizedByUserId; }
    public void setAuthorizedByUserId(String authorizedByUserId) { this.authorizedByUserId = authorizedByUserId; }

    public Instant getOrgLevelSyncedAt() { return orgLevelSyncedAt; }
    public void setOrgLevelSyncedAt(Instant orgLevelSyncedAt) { this.orgLevelSyncedAt = orgLevelSyncedAt; }

    public AzureConnectionStatus getStatus() { return status; }
    public void setStatus(AzureConnectionStatus status) { this.status = status; }

    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public AzureAuthMethod getAuthMethod() { return authMethod; }
    public void setAuthMethod(AzureAuthMethod authMethod) { this.authMethod = authMethod; }

    public String getPersonalAccessToken() { return personalAccessToken; }
    public void setPersonalAccessToken(String personalAccessToken) { this.personalAccessToken = personalAccessToken; }

    public Boolean getReposAccess() { return reposAccess; }
    public void setReposAccess(Boolean reposAccess) { this.reposAccess = reposAccess; }
}

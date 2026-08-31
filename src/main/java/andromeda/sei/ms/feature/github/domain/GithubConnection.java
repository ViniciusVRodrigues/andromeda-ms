package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class GithubConnection {

    private String id;
    private String organizationId;
    private String personalAccessToken;
    private String authorizedByUserId;
    private GithubConnectionStatus status;
    private Instant lastSuccessAt;
    private int consecutiveFailures;
    private String lastErrorCode;
    private String lastErrorMessage;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    /** Optional; {@code null} means the organization never configured one. */
    private WorkItemLinkConfig workItemLinkConfig;

    public GithubConnection(String id, String organizationId, String personalAccessToken,
                             String authorizedByUserId, GithubConnectionStatus status,
                             Instant lastSuccessAt, int consecutiveFailures,
                             String lastErrorCode, String lastErrorMessage,
                             boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.personalAccessToken = personalAccessToken;
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
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getPersonalAccessToken() { return personalAccessToken; }
    public void setPersonalAccessToken(String personalAccessToken) { this.personalAccessToken = personalAccessToken; }

    public String getAuthorizedByUserId() { return authorizedByUserId; }
    public void setAuthorizedByUserId(String authorizedByUserId) { this.authorizedByUserId = authorizedByUserId; }

    public GithubConnectionStatus getStatus() { return status; }
    public void setStatus(GithubConnectionStatus status) { this.status = status; }

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

    /** @return the stored configuration, or the defaults when none was ever set. */
    public WorkItemLinkConfig getWorkItemLinkConfig() {
        return WorkItemLinkConfig.orDefaults(workItemLinkConfig);
    }

    public void setWorkItemLinkConfig(WorkItemLinkConfig workItemLinkConfig) {
        this.workItemLinkConfig = workItemLinkConfig;
    }

    /**
     * The stored value, without the default fallback -- persistence must distinguish
     * "never configured" from "configured to the defaults", otherwise saving a connection
     * would silently freeze today's defaults onto every organization.
     */
    public WorkItemLinkConfig getStoredWorkItemLinkConfig() {
        return workItemLinkConfig;
    }
}

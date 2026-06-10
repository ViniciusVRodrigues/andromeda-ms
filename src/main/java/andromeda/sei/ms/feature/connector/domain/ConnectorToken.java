package andromeda.sei.ms.feature.connector.domain;

import java.time.Instant;

public class ConnectorToken {

    private String id;
    private String organizationId;
    private String userId;
    private String label;
    private String tokenHash;
    private boolean active;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public ConnectorToken(String id, String organizationId, String userId, String label,
                          String tokenHash, boolean active, long version,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.label = label;
        this.tokenHash = tokenHash;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void revoke() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

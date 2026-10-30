package andromeda.sei.ms.feature.agent.domain;

import java.time.Instant;

/**
 * Remembers the response given to a batch, so the same {@code batchId} sent again (lost response,
 * retry) answers the same without reprocessing. Kept by {@code batch-replay-ttl}; the TTL on
 * {@code expiresAt} is, with the purge, the only hard delete. A null response means the batch is
 * still being processed by the request that claimed it.
 */
public class AgentUploadBatch {

    private String id;
    private String organizationId;
    private String deviceId;
    private String batchId;
    private UsageUploadResult response;
    private Instant expiresAt;
    private Long version;
    private Instant createdAt;

    public AgentUploadBatch() {
    }

    public AgentUploadBatch(String organizationId, String deviceId, String batchId,
                            UsageUploadResult response, Instant createdAt, Instant expiresAt) {
        this.organizationId = organizationId;
        this.deviceId = deviceId;
        this.batchId = batchId;
        this.response = response;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public UsageUploadResult getResponse() { return response; }
    public void setResponse(UsageUploadResult response) { this.response = response; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package andromeda.sei.ms.feature.agent.domain;

import java.time.Instant;

/** A pairing request (RFC 8628). Born without organization: the logged-in approver supplies it. */
public class AgentDeviceAuthorization {

    public enum Status { PENDING, APPROVED, DENIED, CONSUMED }

    private String id;
    private String organizationId;
    private String userId;
    private String deviceId;
    private String deviceCodeHash;
    private String userCodeHash;
    private String machineName;
    private String os;
    private String osVersion;
    private String osLabel;
    private String arch;
    private String timezone;
    private String agentVersion;
    private String ip;
    private Status status = Status.PENDING;
    private int intervalSeconds;
    private Instant lastPolledAt;
    private Instant expiresAt;
    private Instant purgeAt;
    private boolean active = true;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceCodeHash() { return deviceCodeHash; }
    public void setDeviceCodeHash(String deviceCodeHash) { this.deviceCodeHash = deviceCodeHash; }
    public String getUserCodeHash() { return userCodeHash; }
    public void setUserCodeHash(String userCodeHash) { this.userCodeHash = userCodeHash; }
    public String getMachineName() { return machineName; }
    public void setMachineName(String machineName) { this.machineName = machineName; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public String getOsLabel() { return osLabel; }
    public void setOsLabel(String osLabel) { this.osLabel = osLabel; }
    public String getArch() { return arch; }
    public void setArch(String arch) { this.arch = arch; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public Instant getLastPolledAt() { return lastPolledAt; }
    public void setLastPolledAt(Instant lastPolledAt) { this.lastPolledAt = lastPolledAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getPurgeAt() { return purgeAt; }
    public void setPurgeAt(Instant purgeAt) { this.purgeAt = purgeAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

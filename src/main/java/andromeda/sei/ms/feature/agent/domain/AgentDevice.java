package andromeda.sei.ms.feature.agent.domain;

import java.time.Instant;
import java.time.LocalDate;

/** A paired local agent (one per machine and person). Tokens are kept only as SHA-256 hashes. */
public class AgentDevice {

    /** The task the person declared active on this device (filled by the task-resolution package). */
    public record ActiveTask(String workItemId, String taskRef, String title, Instant since,
                             String repository, LocalDate day, String branch) {

        public ActiveTask(String workItemId, String taskRef, String title, Instant since, String repository,
                          LocalDate day) {
            this(workItemId, taskRef, title, since, repository, day, null);
        }
    }

    /** Why a task stopped being active: BRANCH_CHANGED, REPO_CHANGED, DAY_ENDED, ITEM_CLOSED, LEFT_SPRINT or CLEARED. */
    public static final java.util.Set<String> END_REASONS = java.util.Set.of(
            "BRANCH_CHANGED", "REPO_CHANGED", "DAY_ENDED", "ITEM_CLOSED", "LEFT_SPRINT", "CLEARED");

    /** The last task that ended on this device (atribuicao-a-prova-de-esquecimento, design 2.3). */
    public record EndedTask(String workItemId, String ref, String title, String repo, String branch,
                            String reason, Instant endedAt, String newBranch, String newRepo) {
    }

    /** The last branch x mark conflict the agent saw (design 2.4). */
    public record TaskConflict(String taskRef, String branch, String branchRef, String repo, Instant at) {
    }

    private String id;
    private String organizationId;
    private String userId;
    private String machineName;
    private String os;
    private String osVersion;
    private String osLabel;
    private String arch;
    private String timezone;
    private String agentVersion;
    private String pairedFromIp;

    private String accessTokenHash;
    private Instant accessTokenExpiresAt;
    private String refreshTokenHash;
    private Instant refreshTokenExpiresAt;
    /** The refresh token replaced by the last rotation; accepted once within the grace window. */
    private String previousRefreshHash;
    private Instant previousRefreshValidUntil;
    private boolean previousRefreshGraceUsed;

    private Instant pairedAt;
    private LocalDate cutoverDay;
    private Instant lastSeenAt;
    private Instant lastUploadAt;
    private Integer pendingRows;
    private ActiveTask activeTask;
    private EndedTask lastEndedTask;
    private TaskConflict taskConflict;

    /** Pending "sync now" request made by a person (absent = none). */
    private Instant syncRequestedAt;
    private String syncRequestedBy;
    /** When the agent last confirmed having served a request. */
    private Instant syncServedAt;

    private Instant revokedAt;
    private String revokedBy;

    private boolean active = true;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public AgentDevice() {
    }

    /** Rotates the pair: the current refresh becomes the previous one (valid once until graceUntil). */
    public void rotateTokens(String newAccessHash, Instant accessExpiresAt,
                             String newRefreshHash, Instant refreshExpiresAt, Instant graceUntil) {
        this.previousRefreshHash = this.refreshTokenHash;
        this.previousRefreshValidUntil = this.refreshTokenHash == null ? null : graceUntil;
        this.previousRefreshGraceUsed = false;
        installTokens(newAccessHash, accessExpiresAt, newRefreshHash, refreshExpiresAt);
    }

    /**
     * The previous refresh was presented again inside the grace window (the agent died before saving
     * the new pair): issues another pair and keeps the same previous hash, now spent.
     */
    public void reissueAfterGrace(String newAccessHash, Instant accessExpiresAt,
                                  String newRefreshHash, Instant refreshExpiresAt) {
        this.previousRefreshGraceUsed = true;
        installTokens(newAccessHash, accessExpiresAt, newRefreshHash, refreshExpiresAt);
    }

    private void installTokens(String newAccessHash, Instant accessExpiresAt,
                               String newRefreshHash, Instant refreshExpiresAt) {
        this.accessTokenHash = newAccessHash;
        this.accessTokenExpiresAt = accessExpiresAt;
        this.refreshTokenHash = newRefreshHash;
        this.refreshTokenExpiresAt = refreshExpiresAt;
    }

    public boolean isPreviousRefreshAcceptable(Instant now) {
        return previousRefreshHash != null && !previousRefreshGraceUsed
                && previousRefreshValidUntil != null && !now.isAfter(previousRefreshValidUntil);
    }

    /** Soft delete: the device stops authenticating and every token hash is dropped. */
    public void revoke(String byUserId, Instant now) {
        this.active = false;
        this.revokedAt = now;
        this.revokedBy = byUserId;
        this.accessTokenHash = null;
        this.accessTokenExpiresAt = null;
        this.refreshTokenHash = null;
        this.refreshTokenExpiresAt = null;
        this.previousRefreshHash = null;
        this.previousRefreshValidUntil = null;
    }

    public boolean isRevoked() {
        return revokedAt != null || !active;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
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
    public String getPairedFromIp() { return pairedFromIp; }
    public void setPairedFromIp(String pairedFromIp) { this.pairedFromIp = pairedFromIp; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public void setAccessTokenHash(String accessTokenHash) { this.accessTokenHash = accessTokenHash; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant v) { this.accessTokenExpiresAt = v; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public void setRefreshTokenHash(String refreshTokenHash) { this.refreshTokenHash = refreshTokenHash; }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public void setRefreshTokenExpiresAt(Instant v) { this.refreshTokenExpiresAt = v; }
    public String getPreviousRefreshHash() { return previousRefreshHash; }
    public void setPreviousRefreshHash(String v) { this.previousRefreshHash = v; }
    public Instant getPreviousRefreshValidUntil() { return previousRefreshValidUntil; }
    public void setPreviousRefreshValidUntil(Instant v) { this.previousRefreshValidUntil = v; }
    public boolean isPreviousRefreshGraceUsed() { return previousRefreshGraceUsed; }
    public void setPreviousRefreshGraceUsed(boolean v) { this.previousRefreshGraceUsed = v; }
    public Instant getPairedAt() { return pairedAt; }
    public void setPairedAt(Instant pairedAt) { this.pairedAt = pairedAt; }
    public LocalDate getCutoverDay() { return cutoverDay; }
    public void setCutoverDay(LocalDate cutoverDay) { this.cutoverDay = cutoverDay; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getLastUploadAt() { return lastUploadAt; }
    public void setLastUploadAt(Instant lastUploadAt) { this.lastUploadAt = lastUploadAt; }
    public Integer getPendingRows() { return pendingRows; }
    public void setPendingRows(Integer pendingRows) { this.pendingRows = pendingRows; }
    public ActiveTask getActiveTask() { return activeTask; }
    public void setActiveTask(ActiveTask activeTask) { this.activeTask = activeTask; }
    public EndedTask getLastEndedTask() { return lastEndedTask; }
    public void setLastEndedTask(EndedTask lastEndedTask) { this.lastEndedTask = lastEndedTask; }
    public TaskConflict getTaskConflict() { return taskConflict; }
    public void setTaskConflict(TaskConflict taskConflict) { this.taskConflict = taskConflict; }
    public Instant getSyncRequestedAt() { return syncRequestedAt; }
    public void setSyncRequestedAt(Instant v) { this.syncRequestedAt = v; }
    public String getSyncRequestedBy() { return syncRequestedBy; }
    public void setSyncRequestedBy(String v) { this.syncRequestedBy = v; }
    public Instant getSyncServedAt() { return syncServedAt; }
    public void setSyncServedAt(Instant v) { this.syncServedAt = v; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

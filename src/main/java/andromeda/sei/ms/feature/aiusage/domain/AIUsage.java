package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class AIUsage {

    private String id;
    private String organizationId;
    private String userId;
    private String gitHubLogin;
    private String datasetId;
    private LocalDate day;
    private String model;
    private String workspaceId;
    private String workspaceName;
    private String machineId;
    private String machineName;
    private long inputTokens;
    private long outputTokens;
    private int interactions;
    private String editor;
    private String allocationId;
    private BigDecimal costUsd;
    private Instant uploadedAt;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    // Local agent rows only (agente-endpoint-v2); all null/zero on AIEF rows.
    private String source;
    private String agentKey;
    private boolean branchApproximate;
    private String deviceId;
    private String tool;
    private String sessionHash;
    private Instant bucketStart;
    private long cacheReadTokens;
    private long cacheWriteTokens;
    private long thinkingTokens;
    private String origin;
    private String repository;
    private String branch;
    private String taskRef;
    private String attributionMethod;
    private String attributionSource;
    private String workItemId;
    private String projectId;
    private String agentVersion;
    private String batchId;
    private String supersededBy;
    private boolean possibleDuplicate;

    public AIUsage(String id, String organizationId, String userId, String gitHubLogin,
                   String datasetId, LocalDate day, String model,
                   String workspaceId, String workspaceName,
                   String machineId, String machineName,
                   long inputTokens, long outputTokens, int interactions,
                   String editor, String allocationId,
                   BigDecimal costUsd,
                   Instant uploadedAt, boolean active, Long version,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.gitHubLogin = gitHubLogin;
        this.datasetId = datasetId;
        this.day = day;
        this.model = model;
        this.workspaceId = workspaceId;
        this.workspaceName = workspaceName;
        this.machineId = machineId;
        this.machineName = machineName;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.interactions = interactions;
        this.editor = editor;
        this.allocationId = allocationId;
        this.costUsd = costUsd;
        this.uploadedAt = uploadedAt;
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

    public String getGitHubLogin() { return gitHubLogin; }
    public void setGitHubLogin(String gitHubLogin) { this.gitHubLogin = gitHubLogin; }

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }

    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getWorkspaceName() { return workspaceName; }
    public void setWorkspaceName(String workspaceName) { this.workspaceName = workspaceName; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public String getMachineName() { return machineName; }
    public void setMachineName(String machineName) { this.machineName = machineName; }

    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }

    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }

    public int getInteractions() { return interactions; }
    public void setInteractions(int interactions) { this.interactions = interactions; }

    public String getEditor() { return editor; }
    public void setEditor(String editor) { this.editor = editor; }

    public String getAllocationId() { return allocationId; }
    public void setAllocationId(String allocationId) { this.allocationId = allocationId; }

    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // --- local agent fields ---
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isBranchApproximate() { return branchApproximate; }
    public void setBranchApproximate(boolean branchApproximate) { this.branchApproximate = branchApproximate; }
    public String getAgentKey() { return agentKey; }
    public void setAgentKey(String agentKey) { this.agentKey = agentKey; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getSessionHash() { return sessionHash; }
    public void setSessionHash(String sessionHash) { this.sessionHash = sessionHash; }
    public Instant getBucketStart() { return bucketStart; }
    public void setBucketStart(Instant bucketStart) { this.bucketStart = bucketStart; }
    public long getCacheReadTokens() { return cacheReadTokens; }
    public void setCacheReadTokens(long cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }
    public long getCacheWriteTokens() { return cacheWriteTokens; }
    public void setCacheWriteTokens(long cacheWriteTokens) { this.cacheWriteTokens = cacheWriteTokens; }
    public long getThinkingTokens() { return thinkingTokens; }
    public void setThinkingTokens(long thinkingTokens) { this.thinkingTokens = thinkingTokens; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getTaskRef() { return taskRef; }
    public void setTaskRef(String taskRef) { this.taskRef = taskRef; }
    public String getAttributionMethod() { return attributionMethod; }
    public void setAttributionMethod(String attributionMethod) { this.attributionMethod = attributionMethod; }
    public String getAttributionSource() { return attributionSource; }
    public void setAttributionSource(String attributionSource) { this.attributionSource = attributionSource; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public boolean isPossibleDuplicate() { return possibleDuplicate; }
    public void setPossibleDuplicate(boolean possibleDuplicate) { this.possibleDuplicate = possibleDuplicate; }
    public String getSupersededBy() { return supersededBy; }
    public void setSupersededBy(String supersededBy) { this.supersededBy = supersededBy; }

    public boolean isAgentRow() { return agentKey != null; }
}

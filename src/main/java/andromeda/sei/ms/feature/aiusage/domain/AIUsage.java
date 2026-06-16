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
}

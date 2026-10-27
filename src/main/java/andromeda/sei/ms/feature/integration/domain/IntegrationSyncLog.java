package andromeda.sei.ms.feature.integration.domain;

import java.time.Instant;

public class IntegrationSyncLog {

    private String id;
    private String organizationId;
    private Integration integration;
    private SyncOperation operation;
    private SyncStatus status;
    private SyncTrigger triggeredBy;
    private String projectId;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private Integer itemsProcessed;
    private Integer itemsFailed;
    private String errorCode;
    private String errorMessage;
    private Integer httpStatus;
    private String errorDetail;
    /** Id do log pai (refresh de projeto ou sync de organizacao) quando este log e um filho por binding. */
    private String parentSyncId;
    /** Binding sincronizado; presente nos logs filhos por binding criados pelo orquestrador. */
    private String bindingId;
    /** Work items deactivated / reactivated by the Azure removal reconciliation of this sync. */
    private int itemsDeactivated;
    private int itemsReactivated;
    /** Itens cujo tipo bruto nao tinha mapeamento (viraram TASK) e ate 20 nomes distintos desses tipos. */
    private int itemsUnmappedType;
    private java.util.List<String> unmappedTypes = java.util.List.of();

    public IntegrationSyncLog(String id, String organizationId, Integration integration,
                               SyncOperation operation, SyncStatus status, SyncTrigger triggeredBy,
                               String projectId, Instant startedAt, Instant finishedAt,
                               Long durationMs, Integer itemsProcessed, Integer itemsFailed,
                               String errorCode, String errorMessage) {
        this.id = id;
        this.organizationId = organizationId;
        this.integration = integration;
        this.operation = operation;
        this.status = status;
        this.triggeredBy = triggeredBy;
        this.projectId = projectId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMs = durationMs;
        this.itemsProcessed = itemsProcessed;
        this.itemsFailed = itemsFailed;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Integration getIntegration() { return integration; }
    public void setIntegration(Integration integration) { this.integration = integration; }

    public SyncOperation getOperation() { return operation; }
    public void setOperation(SyncOperation operation) { this.operation = operation; }

    public SyncStatus getStatus() { return status; }
    public void setStatus(SyncStatus status) { this.status = status; }

    public SyncTrigger getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(SyncTrigger triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getItemsProcessed() { return itemsProcessed; }
    public void setItemsProcessed(Integer itemsProcessed) { this.itemsProcessed = itemsProcessed; }

    public Integer getItemsFailed() { return itemsFailed; }
    public void setItemsFailed(Integer itemsFailed) { this.itemsFailed = itemsFailed; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }

    public String getParentSyncId() { return parentSyncId; }
    public void setParentSyncId(String parentSyncId) { this.parentSyncId = parentSyncId; }

    public String getBindingId() { return bindingId; }
    public void setBindingId(String bindingId) { this.bindingId = bindingId; }

    public int getItemsDeactivated() { return itemsDeactivated; }
    public void setItemsDeactivated(int itemsDeactivated) { this.itemsDeactivated = itemsDeactivated; }

    public int getItemsReactivated() { return itemsReactivated; }
    public void setItemsReactivated(int itemsReactivated) { this.itemsReactivated = itemsReactivated; }

    public int getItemsUnmappedType() { return itemsUnmappedType; }
    public void setItemsUnmappedType(int itemsUnmappedType) { this.itemsUnmappedType = itemsUnmappedType; }

    public java.util.List<String> getUnmappedTypes() { return unmappedTypes; }
    public void setUnmappedTypes(java.util.List<String> unmappedTypes) {
        this.unmappedTypes = unmappedTypes == null ? java.util.List.of() : unmappedTypes;
    }

    /** Verdadeiro enquanto o log nao atingiu um status terminal. */
    public boolean isRunning() { return status == SyncStatus.RUNNING; }
}

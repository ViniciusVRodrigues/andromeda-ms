package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma revisao de um work item no Azure DevOps, com autor e dia (UTC). Fonte da sugestao de
 * alocacao por atividade; nao alimenta metricas de fluxo (essas usam {@link WorkItemTransition}).
 *
 * <p>{@code actorUserId} e nulo quando o autor nao e usuario do Andromeda (ex.: conta de servico).
 */
public class WorkItemActivity {

    private String id;
    private final String organizationId;
    private final String workItemId;
    private final String externalId;
    private final int revision;
    private final String actorExternalId;
    private final String actorUniqueName;
    private final String actorUserId;
    private final Instant changedAt;
    private final LocalDate day;
    private final List<String> changedFields;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkItemActivity(String id, String organizationId, String workItemId, String externalId, int revision,
                            String actorExternalId, String actorUniqueName, String actorUserId,
                            Instant changedAt, LocalDate day, List<String> changedFields,
                            boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.workItemId = workItemId;
        this.externalId = externalId;
        this.revision = revision;
        this.actorExternalId = actorExternalId;
        this.actorUniqueName = actorUniqueName;
        this.actorUserId = actorUserId;
        this.changedAt = changedAt;
        this.day = day;
        this.changedFields = changedFields != null ? List.copyOf(changedFields) : List.of();
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public String getWorkItemId() { return workItemId; }
    public String getExternalId() { return externalId; }
    public int getRevision() { return revision; }
    public String getActorExternalId() { return actorExternalId; }
    public String getActorUniqueName() { return actorUniqueName; }
    public String getActorUserId() { return actorUserId; }
    public Instant getChangedAt() { return changedAt; }
    public LocalDate getDay() { return day; }
    public List<String> getChangedFields() { return changedFields; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

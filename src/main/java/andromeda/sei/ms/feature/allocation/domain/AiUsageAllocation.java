package andromeda.sei.ms.feature.allocation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AiUsageAllocation {

    public enum Status { UNALLOCATED, PENDING_VALIDATION, VALIDATED }
    public enum AssignedBy { AUTO, MANUAL, AGENT }

    /** How the local agent got to the task of an {@code AGENT} allocation (the strongest among its rows). */
    public enum ResolvedBy { MARK, BRANCH, SINGLE_ACTIVE }

    /** Provenance of an {@code AGENT} allocation: distinct repositories and branches of its rows. */
    public record AgentInfo(ResolvedBy resolvedBy, List<String> repositories, List<String> branches,
                            String overriddenTaskRef) {
        public AgentInfo(ResolvedBy resolvedBy, List<String> repositories, List<String> branches) {
            this(resolvedBy, repositories, branches, null);
        }
    }

    /** Why the server wants the person to look at the day before confirming it (atribuicao-a-prova-de-esquecimento, 1.2). */
    public enum ReviewCode { ITEM_CLOSED, OTHER_ASSIGNEE, OUT_OF_SPRINT, USAGE_SPIKE }

    /** One review reason; {@code workItemId}/{@code displayId} are absent on day-level reasons, {@code ratio} only on the spike. */
    public record ReviewReason(ReviewCode code, String workItemId, String displayId, Long ratio) {}


    /** Where an automatic suggestion came from; absent (legacy) is read as {@link #IN_PROGRESS}. */
    public enum SuggestionSource { AZURE_ACTIVITY, IN_PROGRESS }

    private String id;
    private String organizationId;
    private String userId;
    private LocalDate day;
    private List<String> aiUsageIds;
    private List<AllocationSplit> splits;
    private Status status;
    private AssignedBy assignedBy;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private SuggestionSource suggestionSource;
    /** Only on {@code AGENT} allocations; null on every other. */
    private AgentInfo agent;
    /** True on a MANUAL allocation made by moving agent rows to another task (agente-endpoint-v2, design 15). */
    private boolean movedFromAgent;
    /** Never null; empty when there is no reason to review. */
    private List<ReviewReason> reviewReasons = List.of();
    /** When the person confirmed a day that had review reasons. */
    private Instant reviewAcknowledgedAt;

    public AiUsageAllocation(String id, String organizationId, String userId, LocalDate day,
                              List<String> aiUsageIds, List<AllocationSplit> splits,
                              Status status, AssignedBy assignedBy,
                              boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.day = day;
        this.aiUsageIds = aiUsageIds;
        this.splits = splits;
        this.status = status;
        this.assignedBy = assignedBy;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public AiUsageAllocation(String id, String organizationId, String userId, LocalDate day,
                              List<String> aiUsageIds, List<AllocationSplit> splits,
                              Status status, AssignedBy assignedBy,
                              boolean active, Long version, Instant createdAt, Instant updatedAt,
                              SuggestionSource suggestionSource) {
        this(id, organizationId, userId, day, aiUsageIds, splits, status, assignedBy, active, version,
                createdAt, updatedAt);
        this.suggestionSource = suggestionSource;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }

    public List<String> getAiUsageIds() { return aiUsageIds; }
    public void setAiUsageIds(List<String> aiUsageIds) { this.aiUsageIds = aiUsageIds; }

    public List<AllocationSplit> getSplits() { return splits; }
    public void setSplits(List<AllocationSplit> splits) { this.splits = splits; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public AssignedBy getAssignedBy() { return assignedBy; }
    public void setAssignedBy(AssignedBy assignedBy) { this.assignedBy = assignedBy; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** Raw stored value; {@code null} on allocations created before the field existed. */
    public SuggestionSource getSuggestionSource() { return suggestionSource; }
    public void setSuggestionSource(SuggestionSource suggestionSource) { this.suggestionSource = suggestionSource; }

    public AgentInfo getAgent() { return agent; }
    public void setAgent(AgentInfo agent) { this.agent = agent; }

    public boolean isMovedFromAgent() { return movedFromAgent; }
    public void setMovedFromAgent(boolean movedFromAgent) { this.movedFromAgent = movedFromAgent; }

    public List<ReviewReason> getReviewReasons() { return reviewReasons; }
    public void setReviewReasons(List<ReviewReason> reviewReasons) {
        this.reviewReasons = reviewReasons == null ? List.of() : reviewReasons;
    }

    public Instant getReviewAcknowledgedAt() { return reviewAcknowledgedAt; }
    public void setReviewAcknowledgedAt(Instant reviewAcknowledgedAt) { this.reviewAcknowledgedAt = reviewAcknowledgedAt; }

    public boolean hasReviewReasons() { return reviewReasons != null && !reviewReasons.isEmpty(); }

    public boolean isAgent() { return assignedBy == AssignedBy.AGENT; }

    /** Whether this allocation closes the day for the per-day flows (neither the agent's nor a moved correction does). */
    public boolean closesTheDay() { return status == Status.VALIDATED && !isAgent() && !movedFromAgent; }

    /** The source, with an absent value read as {@link SuggestionSource#IN_PROGRESS}. */
    public SuggestionSource suggestionSourceOrDefault() {
        return suggestionSource != null ? suggestionSource : SuggestionSource.IN_PROGRESS;
    }
}

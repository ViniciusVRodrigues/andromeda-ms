package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;
import java.util.List;

/**
 * A pull request of any provider. {@code createdAt} is the pull request's own creation time (the
 * start of its cycle time), not the document's. Keyed by {@code (organizationId, repositoryId,
 * externalId)}.
 */
public class VcsPullRequest {

    private String id;
    private String organizationId;
    private String repositoryId;
    private VcsProvider provider;
    private String externalId;
    private Integer number;
    private String title;
    private PullRequestStatus status;
    private String authorLogin;
    /** Provider id of the author (GitHub numeric id); null on rows migrated from github_pull_requests. */
    private String authorExternalId;
    private String authorUserId;
    private Instant createdAt;
    private Instant mergedAt;
    private Instant closedAt;
    private Instant externalUpdatedAt;
    private Double cycleTimeHours;
    private Double timeToFirstReviewHours;
    private Double timeToFirstApprovalHours;
    /** Work item externalId referenced by this pull request, stored even before the item is synced. */
    private String linkedWorkItemExternalId;
    /** Provider draft flag; {@code null} on legacy rows (read as not draft). */
    private Boolean draft;
    private String baseBranch;
    private String sourceBranch;
    private List<String> labels;
    /** Authoring time of the earliest commit, fetched once after merge (change lead time). */
    private Instant earliestCommitAt;
    private boolean active;
    private Instant updatedAt;
    private Long version;

    public VcsPullRequest(String id, String organizationId, String repositoryId, VcsProvider provider,
                          String externalId, Integer number, String title, PullRequestStatus status) {
        this.id = id;
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.provider = provider;
        this.externalId = externalId;
        this.number = number;
        this.title = title;
        this.status = status;
        this.active = true;
    }

    /** Null-safe: legacy rows without the flag are not drafts. */
    public boolean isDraft() { return Boolean.TRUE.equals(draft); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public VcsProvider getProvider() { return provider; }
    public void setProvider(VcsProvider provider) { this.provider = provider; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public PullRequestStatus getStatus() { return status; }
    public void setStatus(PullRequestStatus status) { this.status = status; }

    public String getAuthorLogin() { return authorLogin; }
    public void setAuthorLogin(String authorLogin) { this.authorLogin = authorLogin; }

    public String getAuthorExternalId() { return authorExternalId; }
    public void setAuthorExternalId(String authorExternalId) { this.authorExternalId = authorExternalId; }

    public String getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(String authorUserId) { this.authorUserId = authorUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public Instant getExternalUpdatedAt() { return externalUpdatedAt; }
    public void setExternalUpdatedAt(Instant externalUpdatedAt) { this.externalUpdatedAt = externalUpdatedAt; }

    public Double getCycleTimeHours() { return cycleTimeHours; }
    public void setCycleTimeHours(Double cycleTimeHours) { this.cycleTimeHours = cycleTimeHours; }

    public Double getTimeToFirstReviewHours() { return timeToFirstReviewHours; }
    public void setTimeToFirstReviewHours(Double timeToFirstReviewHours) { this.timeToFirstReviewHours = timeToFirstReviewHours; }

    public Double getTimeToFirstApprovalHours() { return timeToFirstApprovalHours; }
    public void setTimeToFirstApprovalHours(Double timeToFirstApprovalHours) { this.timeToFirstApprovalHours = timeToFirstApprovalHours; }

    public String getLinkedWorkItemExternalId() { return linkedWorkItemExternalId; }
    public void setLinkedWorkItemExternalId(String linkedWorkItemExternalId) { this.linkedWorkItemExternalId = linkedWorkItemExternalId; }

    public Boolean getDraft() { return draft; }
    public void setDraft(Boolean draft) { this.draft = draft; }

    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }

    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public Instant getEarliestCommitAt() { return earliestCommitAt; }
    public void setEarliestCommitAt(Instant earliestCommitAt) { this.earliestCommitAt = earliestCommitAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class GithubPullRequest {

    private String id;
    private String organizationId;
    private String repoId;
    private String externalId;
    private Integer number;
    private String title;
    private PullRequestStatus status;
    private String authorGithubLogin;
    private String authorUserId;
    private Instant createdAt;
    private Instant mergedAt;
    private Instant closedAt;
    private Instant externalUpdatedAt;
    private Double cycleTimeHours;
    private Double timeToFirstReviewHours;
    /** Azure work item externalId referenced by this PR; null when none was found. */
    private String linkedWorkItemExternalId;
    private boolean active;
    private Instant updatedAt;
    private Long version;

    public GithubPullRequest(String id, String organizationId, String repoId, String externalId, Integer number,
                              String title, PullRequestStatus status, String authorGithubLogin, String authorUserId,
                              Instant createdAt, Instant mergedAt, Instant closedAt, Instant externalUpdatedAt,
                              Double cycleTimeHours, Double timeToFirstReviewHours,
                              boolean active, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.repoId = repoId;
        this.externalId = externalId;
        this.number = number;
        this.title = title;
        this.status = status;
        this.authorGithubLogin = authorGithubLogin;
        this.authorUserId = authorUserId;
        this.createdAt = createdAt;
        this.mergedAt = mergedAt;
        this.closedAt = closedAt;
        this.externalUpdatedAt = externalUpdatedAt;
        this.cycleTimeHours = cycleTimeHours;
        this.timeToFirstReviewHours = timeToFirstReviewHours;
        this.active = active;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getRepoId() { return repoId; }
    public void setRepoId(String repoId) { this.repoId = repoId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public PullRequestStatus getStatus() { return status; }
    public void setStatus(PullRequestStatus status) { this.status = status; }

    public String getAuthorGithubLogin() { return authorGithubLogin; }
    public void setAuthorGithubLogin(String authorGithubLogin) { this.authorGithubLogin = authorGithubLogin; }

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

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getLinkedWorkItemExternalId() { return linkedWorkItemExternalId; }

    public void setLinkedWorkItemExternalId(String linkedWorkItemExternalId) { this.linkedWorkItemExternalId = linkedWorkItemExternalId; }
}

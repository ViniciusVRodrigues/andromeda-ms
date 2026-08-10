package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class GithubReview {

    private String id;
    private String organizationId;
    private String pullRequestId;
    private String externalId;
    private String reviewerGithubLogin;
    private String reviewerUserId;
    private ReviewResult result;
    private Instant submittedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public GithubReview(String id, String organizationId, String pullRequestId, String externalId,
                         String reviewerGithubLogin, String reviewerUserId, ReviewResult result, Instant submittedAt,
                         boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.pullRequestId = pullRequestId;
        this.externalId = externalId;
        this.reviewerGithubLogin = reviewerGithubLogin;
        this.reviewerUserId = reviewerUserId;
        this.result = result;
        this.submittedAt = submittedAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getPullRequestId() { return pullRequestId; }
    public void setPullRequestId(String pullRequestId) { this.pullRequestId = pullRequestId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getReviewerGithubLogin() { return reviewerGithubLogin; }
    public void setReviewerGithubLogin(String reviewerGithubLogin) { this.reviewerGithubLogin = reviewerGithubLogin; }

    public String getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(String reviewerUserId) { this.reviewerUserId = reviewerUserId; }

    public ReviewResult getResult() { return result; }
    public void setResult(ReviewResult result) { this.result = result; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

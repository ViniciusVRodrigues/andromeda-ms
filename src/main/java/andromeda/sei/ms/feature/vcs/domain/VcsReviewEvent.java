package andromeda.sei.ms.feature.vcs.domain;

import java.time.Instant;

/**
 * One normalized review signal (design D3): a GitHub review, and on Azure Repos a vote or a
 * comment. Unique per {@code (organizationId, pullRequestId, externalEventId)}.
 *
 * <p>{@code deleted} marks a signal the provider no longer shows (a withdrawn vote); it never
 * counts for the metrics.
 */
public class VcsReviewEvent {

    private String id;
    private String organizationId;
    private String pullRequestId;
    private String repositoryId;
    private VcsProvider provider;
    private String externalEventId;
    private String actorExternalId;
    private String actorLogin;
    private String actorUserId;
    private boolean bot;
    private ReviewKind kind;
    private Instant occurredAt;
    private boolean deleted;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public VcsReviewEvent(String id, String organizationId, String pullRequestId, String repositoryId,
                          VcsProvider provider, String externalEventId, ReviewKind kind, Instant occurredAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.pullRequestId = pullRequestId;
        this.repositoryId = repositoryId;
        this.provider = provider;
        this.externalEventId = externalEventId;
        this.kind = kind;
        this.occurredAt = occurredAt;
        this.active = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getPullRequestId() { return pullRequestId; }
    public void setPullRequestId(String pullRequestId) { this.pullRequestId = pullRequestId; }

    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public VcsProvider getProvider() { return provider; }
    public void setProvider(VcsProvider provider) { this.provider = provider; }

    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String externalEventId) { this.externalEventId = externalEventId; }

    public String getActorExternalId() { return actorExternalId; }
    public void setActorExternalId(String actorExternalId) { this.actorExternalId = actorExternalId; }

    public String getActorLogin() { return actorLogin; }
    public void setActorLogin(String actorLogin) { this.actorLogin = actorLogin; }

    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }

    public boolean isBot() { return bot; }
    public void setBot(boolean bot) { this.bot = bot; }

    public ReviewKind getKind() { return kind; }
    public void setKind(ReviewKind kind) { this.kind = kind; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** {@code vcs_pull_requests}: the listing, filter and merge indexes inherited from github_pull_requests. */
@Document(collection = VcsPullRequestDocument.COLLECTION)
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'externalId': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'authorUserId': 1, 'createdAt': 1}"),
        // Repository listing: scoped to org+repo, optionally by status, newest first.
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'status': 1, 'createdAt': -1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'createdAt': -1}"),
        // Delivery metrics: merged pull requests of a repository set in a window.
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'status': 1, 'mergedAt': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'provider': 1}"),
        // pattern detection (agente-endpoint-v2 6.3): distinct recent branches of the whole organization
        @CompoundIndex(def = "{'organizationId': 1, 'createdAt': -1}", name = "idx_vcs_pr_org_created")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcsPullRequestDocument {

    public static final String COLLECTION = "vcs_pull_requests";

    @Id
    private String id;

    private String organizationId;
    private String repositoryId;
    private VcsProvider provider;
    private String externalId;
    private Integer number;
    private String title;
    private PullRequestStatus status;
    private String authorLogin;
    private String authorExternalId;
    private String authorUserId;
    private Instant createdAt;
    private Instant mergedAt;
    private Instant closedAt;
    private Instant externalUpdatedAt;
    private Double cycleTimeHours;
    private Double timeToFirstReviewHours;
    private Double timeToFirstApprovalHours;
    private String linkedWorkItemExternalId;
    private Boolean draft;
    private String baseBranch;
    private String sourceBranch;
    private List<String> labels;
    private Instant earliestCommitAt;
    @Builder.Default
    private boolean active = true;
    private Instant updatedAt;

    @Version
    private Long version;
}

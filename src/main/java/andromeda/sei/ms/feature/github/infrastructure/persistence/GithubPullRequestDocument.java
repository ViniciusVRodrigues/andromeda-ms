package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
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

@Document(collection = "github_pull_requests")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1, 'externalId': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'authorUserId': 1, 'createdAt': 1}"),
        // Serves the repository pull request listing: always scoped to org+repo, optionally
        // narrowed by status, and always ordered newest-first. Descending on createdAt so the
        // index order matches the sort and Mongo does not have to sort in memory; the existing
        // {org, repo} index cannot serve either the status filter or the ordering.
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1, 'status': 1, 'createdAt': -1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1, 'createdAt': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubPullRequestDocument {

    @Id
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
    private String linkedWorkItemExternalId;
    private boolean active = true;
    private Instant updatedAt;

    @Version
    private Long version;
}

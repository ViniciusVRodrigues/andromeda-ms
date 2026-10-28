package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
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

@Document(collection = VcsRepoBindingDocument.COLLECTION)
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'projectId': 1, 'repositoryId': 1, 'active': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'active': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'provider': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcsRepoBindingDocument {

    public static final String COLLECTION = "vcs_repo_bindings";

    @Id
    private String id;

    private String organizationId;
    private String projectId;
    private String repositoryId;
    private VcsProvider provider;
    private boolean syncEnabled;
    private Instant lastSyncAt;
    @Builder.Default
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    /** {@code null} on bindings created before branches-por-vinculo: they follow the default branch. */
    private List<String> branches;
    private String productionBranch;
    private boolean filterPullRequestsByBranch;
    private List<String> missingBranches;
    private VcsSyncState syncState;
    private Instant syncStateAt;

    @Version
    private Long version;
}

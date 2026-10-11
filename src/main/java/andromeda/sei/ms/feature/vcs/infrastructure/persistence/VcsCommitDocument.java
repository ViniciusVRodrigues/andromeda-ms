package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

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

@Document(collection = VcsCommitDocument.COLLECTION)
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'sha': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'authoredAt': 1}"),
        // covers the per-day commit counts of the organization dashboard (no document fetch)
        @CompoundIndex(def = "{'organizationId': 1, 'repositoryId': 1, 'active': 1, 'authoredAt': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcsCommitDocument {

    public static final String COLLECTION = "vcs_commits";

    @Id
    private String id;

    private String organizationId;
    private String repositoryId;
    private VcsProvider provider;
    private String sha;
    private String message;
    private String authorLogin;
    private String authorUserId;
    private Instant authoredAt;
    @Builder.Default
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

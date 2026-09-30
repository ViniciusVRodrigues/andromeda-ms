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

/**
 * {@code vcs_repositories} (design D2). The unique identity {@code {organizationId, provider,
 * externalRepoId}} is a <em>partial</em> index (only where {@code externalRepoId} exists, so
 * repositories pending first sync do not collide). Annotations cannot express it, so
 * {@link VcsIndexInitializer} creates it.
 */
@Document(collection = VcsRepositoryDocument.COLLECTION)
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'provider': 1, 'displayName': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcsRepositoryDocument {

    public static final String COLLECTION = "vcs_repositories";

    @Id
    private String id;

    private String organizationId;
    private VcsProvider provider;
    private String externalRepoId;
    private String externalProjectId;
    private String displayName;
    private String defaultBranch;
    private Instant externalUpdatedAt;
    @Builder.Default
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

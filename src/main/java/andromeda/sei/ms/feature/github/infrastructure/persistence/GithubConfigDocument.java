package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
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

@Document(collection = "github_configs")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubConfigDocument {

    @Id
    private String id;

    private String organizationId;
    private String personalAccessToken;
    private String authorizedByUserId;
    private GithubConnectionStatus status;
    private Instant lastSuccessAt;
    private int consecutiveFailures;
    private String lastErrorCode;
    private String lastErrorMessage;
    /**
     * Legacy (vcs-core-refactor): the work item link configuration now lives in
     * {@code organizations.vcsSettings}. Kept, read-only, for one release so a rollback still
     * finds it; {@link GithubConfigRepositoryImpl#save} carries it over untouched.
     */
    @Deprecated
    private WorkItemLinkConfigDocument workItemLinkConfig;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    /** Embedded per the AzureProjectBindingDocument.WorkflowConfigDocument precedent. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkItemLinkConfigDocument {
        private boolean enabled;
        private java.util.List<String> prefixes;
    }
}

package andromeda.sei.ms.feature.github.infrastructure.persistence;

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

@Document(collection = "github_commits")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1, 'sha': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1, 'authoredAt': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubCommitDocument {

    @Id
    private String id;

    private String organizationId;
    private String repoId;
    private String sha;
    private String message;
    private String authorGithubLogin;
    private String authorUserId;
    private Instant authoredAt;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

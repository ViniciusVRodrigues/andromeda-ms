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

@Document(collection = "github_repos")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'externalId': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepoDocument {

    @Id
    private String id;

    private String organizationId;
    private String externalId;
    private String owner;
    private String name;
    private String fullName;
    private Instant externalUpdatedAt;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

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

@Document(collection = "repo_metrics")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'repoId': 1, 'periodStart': 1, 'periodEnd': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoMetricsDocument {

    @Id
    private String id;

    private String organizationId;
    private String repoId;
    private Instant periodStart;
    private Instant periodEnd;
    private Double cfr;
    private boolean estimated;
    private Instant calculatedAt;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

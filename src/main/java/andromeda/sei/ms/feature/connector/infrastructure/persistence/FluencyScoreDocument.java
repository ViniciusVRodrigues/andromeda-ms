package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "fluency_scores")
@CompoundIndex(def = "{'organizationId': 1, 'userId': 1}", unique = true, name = "uq_fluency_score_user_org")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FluencyScoreDocument {

    @Id
    private String id;

    private String organizationId;
    private String userId;

    private int overallStage;
    private String overallLabel;
    private String computedAt;
    private List<FluencyScoreCategoryDocument> categories;

    @lombok.Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}

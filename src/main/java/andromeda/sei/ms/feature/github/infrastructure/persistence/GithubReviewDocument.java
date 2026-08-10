package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.ReviewResult;
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

@Document(collection = "github_reviews")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'pullRequestId': 1, 'externalId': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'pullRequestId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubReviewDocument {

    @Id
    private String id;

    private String organizationId;
    private String pullRequestId;
    private String externalId;
    private String reviewerGithubLogin;
    private String reviewerUserId;
    private ReviewResult result;
    private Instant submittedAt;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

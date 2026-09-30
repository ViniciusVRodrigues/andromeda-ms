package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
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
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = VcsReviewEventDocument.COLLECTION)
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'pullRequestId': 1, 'externalEventId': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'pullRequestId': 1, 'occurredAt': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VcsReviewEventDocument {

    public static final String COLLECTION = "vcs_review_events";

    @Id
    private String id;

    private String organizationId;
    private String pullRequestId;
    private String repositoryId;
    private VcsProvider provider;
    private String externalEventId;
    private String actorExternalId;
    private String actorLogin;
    private String actorUserId;
    @Field("isBot")
    private boolean bot;
    private ReviewKind kind;
    private Instant occurredAt;
    private boolean deleted;
    @Builder.Default
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

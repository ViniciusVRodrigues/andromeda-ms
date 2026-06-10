package andromeda.sei.ms.feature.connector.infrastructure.persistence;

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

@Document(collection = "connector_tokens")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'tokenHash': 1}", unique = true),
        @CompoundIndex(def = "{'organizationId': 1, 'userId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorTokenDocument {

    @Id
    private String id;

    private String organizationId;
    private String userId;
    private String label;
    private String tokenHash;

    @Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}

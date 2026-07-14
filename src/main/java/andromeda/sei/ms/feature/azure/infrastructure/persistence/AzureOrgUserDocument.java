package andromeda.sei.ms.feature.azure.infrastructure.persistence;

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

@Document(collection = "azure_org_users")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'azureId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AzureOrgUserDocument {

    @Id
    private String id;

    private String organizationId;
    private String azureId;
    private String displayName;
    private String azureEmail;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}

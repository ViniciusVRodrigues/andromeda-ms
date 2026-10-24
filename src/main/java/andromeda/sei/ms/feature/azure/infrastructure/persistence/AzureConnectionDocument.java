package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
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

@Document(collection = "azure_connections")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AzureConnectionDocument {

    @Id
    private String id;

    private String organizationId;
    private String azureOrgUrl;
    private String accessToken;
    private String refreshToken;
    private Instant tokenExpiresAt;
    private String authorizedByUserId;
    private AzureConnectionStatus status;
    private Instant lastSuccessAt;
    private int consecutiveFailures;
    private String lastErrorCode;
    private String lastErrorMessage;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;
    private AzureAuthMethod authMethod;
    private String personalAccessToken;
    private Boolean reposAccess;
    private java.time.Instant orgLevelSyncedAt;

    @Version
    private Long version;
}

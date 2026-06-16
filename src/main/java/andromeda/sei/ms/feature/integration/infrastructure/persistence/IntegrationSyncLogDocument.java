package andromeda.sei.ms.feature.integration.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "integration_sync_logs")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'integration': 1, 'startedAt': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationSyncLogDocument {

    @Id
    private String id;

    private String organizationId;
    private Integration integration;
    private SyncOperation operation;
    private SyncStatus status;
    private SyncTrigger triggeredBy;
    private String projectId;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private Integer itemsProcessed;
    private Integer itemsFailed;
    private String errorCode;
    private String errorMessage;
    private Integer httpStatus;
    private String errorDetail;
}

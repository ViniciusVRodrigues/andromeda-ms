package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Response stored for an agent upload batch. Exception to soft delete (ephemeral replay data): the
 * TTL on {@code expiresAt} removes it, as the purge of the organization does.
 */
@Document(collection = "agent_upload_batches")
@CompoundIndex(name = "uq_agent_upload_batch", def = "{'organizationId': 1, 'deviceId': 1, 'batchId': 1}", unique = true)
@Data
@NoArgsConstructor
public class AgentUploadBatchDocument {

    @Id
    private String id;
    private String organizationId;
    private String deviceId;
    private String batchId;
    private boolean active = true;
    /** Null while the batch is in progress (claimed at {@code claimedAt}, agt10). */
    private ResponseDocument response;
    private Instant claimedAt;

    @Indexed(name = "ttl_agent_upload_batch_expires", expireAfter = "0s")
    private Instant expiresAt;

    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    public static class ResponseDocument {
        private String batchId;
        private int accepted;
        private int duplicate;
        private int rejected;
        private List<RowDocument> results;
    }

    @Data
    @NoArgsConstructor
    public static class RowDocument {
        private String key;
        private String status;
        private String allocation;
        private String workItemId;
        private String taskRef;
        private String reason;
        private String field;
        private Boolean retryable;
    }
}

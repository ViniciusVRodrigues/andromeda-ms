package andromeda.sei.ms.feature.agent.infrastructure.persistence;

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
import java.time.LocalDate;

/**
 * Paired local agent. Token hashes are unique only while set (partial index): a revoked device drops
 * them, so many revoked devices coexist.
 */
@Document(collection = "agent_devices")
@CompoundIndexes({
        @CompoundIndex(name = "idx_agent_device_org_user_active",
                def = "{'organizationId': 1, 'userId': 1, 'active': 1}"),
        @CompoundIndex(name = "uq_agent_device_access_hash", def = "{'accessTokenHash': 1}", unique = true,
                partialFilter = "{'accessTokenHash': {$type: 'string'}}"),
        @CompoundIndex(name = "uq_agent_device_refresh_hash", def = "{'refreshTokenHash': 1}", unique = true,
                partialFilter = "{'refreshTokenHash': {$type: 'string'}}"),
        @CompoundIndex(name = "idx_agent_device_previous_refresh", def = "{'previousRefreshHash': 1}",
                partialFilter = "{'previousRefreshHash': {$type: 'string'}}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDeviceDocument {

    @Id
    private String id;
    private String organizationId;
    private String userId;
    private String machineName;
    private String os;
    private String osVersion;
    private String osLabel;
    private String arch;
    private String timezone;
    private String agentVersion;
    private String pairedFromIp;

    private String accessTokenHash;
    private Instant accessTokenExpiresAt;
    private String refreshTokenHash;
    private Instant refreshTokenExpiresAt;
    private String previousRefreshHash;
    private Instant previousRefreshValidUntil;
    private boolean previousRefreshGraceUsed;

    private Instant pairedAt;
    private LocalDate cutoverDay;
    private Instant lastSeenAt;
    private Instant lastUploadAt;
    private Integer pendingRows;
    private ActiveTaskDocument activeTask;

    private Instant syncRequestedAt;
    private String syncRequestedBy;
    private Instant syncServedAt;

    private Instant revokedAt;
    private String revokedBy;

    @Builder.Default
    private boolean active = true;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveTaskDocument {
        private String workItemId;
        private String taskRef;
        private String title;
        private Instant since;
        private String repository;
        private LocalDate day;
    }
}

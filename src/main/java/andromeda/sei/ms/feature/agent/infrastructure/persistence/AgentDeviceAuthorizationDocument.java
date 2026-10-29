package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Pairing request. Exception to soft delete (as the OAuth link tickets): ephemeral security data, the
 * TTL on {@code purgeAt} (expiry + 1 day) removes it. Holds only hashes of the codes.
 */
@Document(collection = "agent_device_authorizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDeviceAuthorizationDocument {

    @Id
    private String id;

    /** Null until the approval. */
    private String organizationId;
    private String userId;
    private String deviceId;

    @Indexed(name = "uq_agent_authorization_device_code", unique = true)
    private String deviceCodeHash;
    @Indexed(name = "uq_agent_authorization_user_code", unique = true)
    private String userCodeHash;

    private String machineName;
    private String os;
    private String osVersion;
    private String osLabel;
    private String arch;
    private String timezone;
    private String agentVersion;
    private String ip;
    private String status;
    private int intervalSeconds;
    private Instant lastPolledAt;
    private Instant expiresAt;

    @Indexed(name = "ttl_agent_authorization_purge", expireAfter = "0s")
    private Instant purgeAt;

    @Builder.Default
    private boolean active = true;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}

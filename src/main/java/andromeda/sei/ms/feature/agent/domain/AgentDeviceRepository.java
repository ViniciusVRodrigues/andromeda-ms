package andromeda.sei.ms.feature.agent.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port of the paired devices. Every lookup is scoped by organization except the token ones. */
public interface AgentDeviceRepository {

    AgentDevice save(AgentDevice device);

    /** True when the organization already has any device, revoked ones included. */
    boolean existsByOrganizationId(String organizationId);

    /** Active device by id in the organization (any user). */
    Optional<AgentDevice> findActiveByIdAndOrganizationId(String id, String organizationId);

    /** Device by id in the organization, revoked ones included. */
    Optional<AgentDevice> findByIdAndOrganizationId(String id, String organizationId);

    Optional<AgentDevice> findByAccessTokenHash(String accessTokenHash);

    Optional<AgentDevice> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AgentDevice> findByPreviousRefreshHash(String previousRefreshHash);

    /** The person's devices, revoked ones included (callers filter the age of the revoked). */
    List<AgentDevice> findDevicesOfUser(String organizationId, String userId);

    /** Targeted writes (no full replace, no version bump): safe against concurrent requests. */
    void touchSeen(String deviceId, String organizationId, Instant seenAt, String agentVersion);

    void recordHeartbeat(String deviceId, String organizationId, Instant seenAt, String agentVersion,
                         Integer pendingRows, String timezone);

    /** Sets the timezone the agent reported (the person travelled). */
    void updateTimezone(String deviceId, String organizationId, String timezone);

    void touchUpload(String deviceId, String organizationId, Instant uploadAt);

    /** Records a "sync now" request only when none is pending (atomic). Returns true when it wrote. */
    boolean requestSync(String deviceId, String organizationId, Instant at, String by);

    /** Clears the pending request only if it is still {@code requestedAt}; sets {@code syncServedAt}. */
    void clearServedSync(String deviceId, String organizationId, Instant requestedAt, Instant servedAt);

    void setActiveTask(String deviceId, String organizationId, AgentDevice.ActiveTask task);

    /** Ends the active task: records {@code ended}, clears the task and the task conflict (atomic). */
    void endActiveTask(String deviceId, String organizationId, AgentDevice.EndedTask ended);

    /** Stores the last conflict the agent saw; null clears it. */
    void setTaskConflict(String deviceId, String organizationId, AgentDevice.TaskConflict conflict);
}

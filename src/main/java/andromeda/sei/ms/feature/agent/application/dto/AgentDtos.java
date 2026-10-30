package andromeda.sei.ms.feature.agent.application.dto;

import java.time.Instant;

/** Inputs and outputs of the pairing / device use cases. */
public final class AgentDtos {

    private AgentDtos() {
    }

    public record DeviceCodeInput(String agentVersion, String machineName, String os, String osVersion,
                                  String osLabel, String arch, String timezone, String ip) {
    }

    /** {@code userCode} is already formatted {@code XXXX-XXXX}. */
    public record DeviceCodeOutput(String deviceCode, String userCode, long expiresInSeconds, int intervalSeconds) {
    }

    /** {@code status}: PENDING, APPROVED, DENIED, EXPIRED or USED. {@code sameNetworkAsBrowser} may be null. */
    public record PairingOutput(String userCode, String status, String machineName, String os, String osLabel,
                                String arch, String agentVersion, String requesterIp,
                                Boolean sameNetworkAsBrowser, Instant requestedAt, Instant expiresAt,
                                String organizationName, String userName) {
    }

    public record ApprovedDeviceOutput(String deviceId, String machineName) {
    }

    public record TokenPairOutput(String accessToken, String refreshToken, long expiresInSeconds, String scope,
                                  String deviceId, String organizationSlug) {
    }

    public record ActiveTaskOutput(String workItemId, String ref, String title, Instant since, String repo,
                                   String branch, java.time.LocalDate day) {

        public ActiveTaskOutput(String workItemId, String ref, String title, Instant since, String repo) {
            this(workItemId, ref, title, since, repo, null, null);
        }
    }

    /** The task that ended on the device (design 2.3); {@code reason} is one of AgentDevice.END_REASONS. */
    public record EndedTaskOutput(String workItemId, String ref, String title, String repo, String branch,
                                  String reason, Instant endedAt, String newBranch, String newRepo) {
    }

    public record TaskConflictOutput(String taskRef, String branch, String branchRef, String repo, Instant at) {
    }

    public record OsOutput(String family, String label, String arch) {
    }

    /** {@code versionStatus}: UNSUPPORTED (below min), OUTDATED (below recommended) or OK. */
    public record DeviceOutput(String id, String machineName, OsOutput os, String agentVersion,
                               String versionStatus, Instant pairedAt, Instant lastSeenAt, Instant lastUploadAt,
                               String status, long daysWithoutContact, boolean outdated, String minVersion,
                               ActiveTaskOutput activeTask, Instant revokedAt, Instant syncRequestedAt,
                               Instant syncServedAt, boolean syncNowSupported) {
    }

    public record AgentMetaOutput(String packageName, String latestVersion, String recommendedVersion,
                                  String minVersion, int nodeMin, String privacyUrl, String taskClearCommand,
                                  boolean aiefIgnored) {
    }

    /** {@code taskConflict} null = the agent did not send the field (keep); otherwise a clear or a new value. */
    public record HeartbeatInput(String agentVersion, String timezone, Integer pendingRows, String syncServed,
                                 ConflictUpdate taskConflict) {

        public HeartbeatInput(String agentVersion, String timezone, Integer pendingRows, String syncServed) {
            this(agentVersion, timezone, pendingRows, syncServed, null);
        }

        public record ConflictUpdate(andromeda.sei.ms.feature.agent.domain.AgentDevice.TaskConflict value) {
            public static ConflictUpdate clear() {
                return new ConflictUpdate(null);
            }

            public static ConflictUpdate set(andromeda.sei.ms.feature.agent.domain.AgentDevice.TaskConflict v) {
                return new ConflictUpdate(v);
            }
        }
    }

    public record HeartbeatOutput(Instant serverTime, String minVersion, ActiveTaskOutput activeTask,
                                  Instant syncRequestedAt, EndedTaskOutput activeTaskEnded) {

        public HeartbeatOutput(Instant serverTime, String minVersion, ActiveTaskOutput activeTask,
                               Instant syncRequestedAt) {
            this(serverTime, minVersion, activeTask, syncRequestedAt, null);
        }
    }

    public record SyncRequestOutput(String deviceId, Instant syncRequestedAt, Instant syncServedAt) {
    }
}

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

    public record ActiveTaskOutput(String workItemId, String ref, String title, Instant since, String repo) {
    }

    public record OsOutput(String family, String label, String arch) {
    }

    /** {@code versionStatus}: UNSUPPORTED (below min), OUTDATED (below recommended) or OK. */
    public record DeviceOutput(String id, String machineName, OsOutput os, String agentVersion,
                               String versionStatus, Instant pairedAt, Instant lastSeenAt, Instant lastUploadAt,
                               String status, long daysWithoutContact, boolean outdated, String minVersion,
                               ActiveTaskOutput activeTask, Instant revokedAt) {
    }

    public record AgentMetaOutput(String packageName, String latestVersion, String recommendedVersion,
                                  String minVersion, int nodeMin, String privacyUrl, String taskClearCommand,
                                  boolean aiefIgnored) {
    }

    public record HeartbeatInput(String agentVersion, String timezone, Integer pendingRows) {
    }

    public record HeartbeatOutput(Instant serverTime, String minVersion, ActiveTaskOutput activeTask) {
    }
}

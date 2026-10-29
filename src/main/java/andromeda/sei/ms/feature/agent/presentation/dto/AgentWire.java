package andromeda.sei.ms.feature.agent.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Wire formats of the agent and pairing endpoints (snake_case where the RFC asks for it). */
public final class AgentWire {

    private AgentWire() {
    }

    public record DeviceCodeRequest(
            @NotBlank @Size(max = 40) String agentVersion,
            @NotBlank @Size(max = 64) String machineName,
            @NotBlank @Size(max = 16) String os,
            @Size(max = 64) String osVersion,
            @Size(max = 64) String osLabel,
            @Size(max = 16) String arch,
            @NotBlank @Size(max = 64) String timezone) {
    }

    public record DeviceCodeResponse(
            @JsonProperty("device_code") String deviceCode,
            @JsonProperty("user_code") String userCode,
            @JsonProperty("verification_uri") String verificationUri,
            @JsonProperty("verification_uri_complete") String verificationUriComplete,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("interval") int interval) {
    }

    public record DeviceTokenRequest(
            @JsonProperty("grant_type") String grantType,
            @JsonProperty("device_code") String deviceCode,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("scope") String scope,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("organization_slug") String organizationSlug) {
    }

    public record OAuthError(
            @JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription) {
    }

    public record HeartbeatRequest(
            @Size(max = 40) String agentVersion,
            @Size(max = 64) String timezone,
            Integer pendingRows,
            String oldestPendingDay) {
    }

    /** Body of {@code PUT /agent/v1/active-task}; {@code since} defaults to now. */
    public record ActiveTaskRequest(
            @NotBlank @Size(max = 64) String taskRef,
            java.time.Instant since,
            @NotNull java.time.LocalDate day,
            @Size(max = 300) String repo) {
    }

    public record ActiveTaskResponse(String workItemId, String ref, String title, int retroactiveRows) {
    }

    /** Body of {@code POST /api/v1/ai/allocations/{allocationId}/move}. */
    public record MoveUsageRequest(
            @NotEmpty @Size(max = 2000) java.util.List<@NotBlank String> aiUsageIds,
            @NotBlank String workItemId) {
    }

    /** Response of {@code POST /agent/v1/usage}. */
    public record UsageUploadResponse(String batchId, int accepted, int duplicate, int rejected,
                                      java.util.List<UsageRowResponse> results) {
    }

    /** One row of the upload response; absent fields are omitted. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UsageRowResponse(String key, String status, String allocation, String workItemId,
                                   String taskRef, String reason, String field, Boolean retryable) {
    }

    public record ReconcileTokens(long input, long output, long cacheRead, long cacheWrite, long thinking) {
    }

    public record ReconcileDay(java.time.LocalDate day, int rows, ReconcileTokens tokens, String keysDigest) {
    }

    public record ReconcileResponse(String deviceId, java.util.List<ReconcileDay> days) {
    }

    public record KeysResponse(java.time.LocalDate day, java.util.List<String> keys) {
    }

    public record TaskResponse(String workItemId, String ref, String externalId, String title, String type,
                               String sprintName) {
    }

    public record TasksResponse(java.util.List<TaskResponse> items) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VersionUnsupportedResponse(String code, String message, String minVersion) {
    }
}

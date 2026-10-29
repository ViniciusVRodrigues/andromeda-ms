package andromeda.sei.ms.feature.agent.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
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

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VersionUnsupportedResponse(String code, String message, String minVersion) {
    }
}

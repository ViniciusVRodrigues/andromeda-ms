package andromeda.sei.ms.feature.azure.application.dto;

import java.time.Instant;

/**
 * What the OAuth callback hands to the connection use case. Tokens are raw here (never logged) and
 * encrypted before anything is stored.
 *
 * @param orgUrl the organization URL informed at authorization start, carried by the {@code state};
 *               {@code null} when the callback's state did not carry one
 */
public record AzureOAuthCallbackInput(String organizationId, String userId, String orgUrl, String accessToken,
                                      String refreshToken, Instant tokenExpiresAt) {

    @Override
    public String toString() {
        return "AzureOAuthCallbackInput[organizationId=" + organizationId + ", userId=" + userId + ", orgUrl=" + orgUrl
                + ", tokenExpiresAt=" + tokenExpiresAt + "]";
    }
}

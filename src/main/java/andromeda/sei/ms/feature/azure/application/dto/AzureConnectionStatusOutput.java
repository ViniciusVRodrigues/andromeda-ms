package andromeda.sei.ms.feature.azure.application.dto;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;

import java.time.Instant;

/**
 * @param reposAccess    the credential can read Git repositories (Code (Read)); {@code null} when unknown
 * @param lastErrorCode  why the connection is in {@code ERROR}, e.g. {@code AZURE_ORG_URL_INVALID}
 *                       (the UI then offers the URL fix without reauthorizing)
 */
public record AzureConnectionStatusOutput(
        AzureConnectionStatus status,
        String azureOrgUrl,
        String authorizedByUserId,
        Instant lastSuccessAt,
        int consecutiveFailures,
        AzureAuthMethod authMethod,
        Boolean reposAccess,
        String lastErrorCode,
        String lastErrorMessage
) {

    public AzureConnectionStatusOutput(AzureConnectionStatus status, String azureOrgUrl, String authorizedByUserId,
                                       Instant lastSuccessAt, int consecutiveFailures, AzureAuthMethod authMethod) {
        this(status, azureOrgUrl, authorizedByUserId, lastSuccessAt, consecutiveFailures, authMethod, null, null, null);
    }

    public static AzureConnectionStatusOutput of(andromeda.sei.ms.feature.azure.domain.AzureConnection connection) {
        return new AzureConnectionStatusOutput(connection.getStatus(), connection.getAzureOrgUrl(),
                connection.getAuthorizedByUserId(), connection.getLastSuccessAt(), connection.getConsecutiveFailures(),
                connection.getAuthMethod(), connection.getReposAccess(), connection.getLastErrorCode(),
                connection.getLastErrorMessage());
    }
}

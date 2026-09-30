package andromeda.sei.ms.feature.azure.presentation.dto;

import andromeda.sei.ms.feature.azure.application.dto.AzureConnectionStatusOutput;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;

import java.time.Instant;

/**
 * Azure DevOps connection status. {@code reposAccess}: whether the credential can read Git
 * repositories (Code (Read)), {@code null} when never checked. {@code lastErrorCode} /
 * {@code lastErrorMessage}: why the connection is in {@code ERROR} ({@code AZURE_ORG_URL_INVALID}
 * means the organization URL must be fixed, see {@code PUT /connection/org-url}).
 */
public record AzureConnectionStatusResponse(
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

    public static AzureConnectionStatusResponse from(AzureConnectionStatusOutput output) {
        return new AzureConnectionStatusResponse(output.status(), output.azureOrgUrl(), output.authorizedByUserId(),
                output.lastSuccessAt(), output.consecutiveFailures(), output.authMethod(), output.reposAccess(),
                output.lastErrorCode(), output.lastErrorMessage());
    }
}

package andromeda.sei.ms.feature.azure.presentation.dto;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;

import java.time.Instant;

public record AzureConnectionStatusResponse(
        AzureConnectionStatus status,
        String azureOrgUrl,
        String authorizedByUserId,
        Instant lastSuccessAt,
        int consecutiveFailures,
        AzureAuthMethod authMethod
) {}

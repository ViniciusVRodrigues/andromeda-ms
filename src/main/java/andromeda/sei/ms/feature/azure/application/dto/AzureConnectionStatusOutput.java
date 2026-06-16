package andromeda.sei.ms.feature.azure.application.dto;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;

import java.time.Instant;

public record AzureConnectionStatusOutput(
        AzureConnectionStatus status,
        String azureOrgUrl,
        String authorizedByUserId,
        Instant lastSuccessAt,
        int consecutiveFailures,
        AzureAuthMethod authMethod
) {}

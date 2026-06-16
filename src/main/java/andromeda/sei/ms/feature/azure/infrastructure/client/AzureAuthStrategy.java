package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;

public interface AzureAuthStrategy {
    String getAuthorizationHeader(AzureConnection connection);
}

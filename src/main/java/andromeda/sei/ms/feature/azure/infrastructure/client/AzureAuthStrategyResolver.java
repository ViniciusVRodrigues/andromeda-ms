package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureAuthStrategyResolver {

    private final OAuthAzureAuthStrategy oAuthAzureAuthStrategy;
    private final PatAzureAuthStrategy patAzureAuthStrategy;

    public String resolve(AzureConnection connection) {
        if (connection.getAuthMethod() == AzureAuthMethod.PAT) {
            return patAzureAuthStrategy.getAuthorizationHeader(connection);
        }
        return oAuthAzureAuthStrategy.getAuthorizationHeader(connection);
    }
}

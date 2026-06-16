package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthAzureAuthStrategy implements AzureAuthStrategy {

    private final EncryptionService encryptionService;

    @Override
    public String getAuthorizationHeader(AzureConnection connection) {
        String decryptedToken = encryptionService.decrypt(connection.getAccessToken());
        return "Bearer " + decryptedToken;
    }
}

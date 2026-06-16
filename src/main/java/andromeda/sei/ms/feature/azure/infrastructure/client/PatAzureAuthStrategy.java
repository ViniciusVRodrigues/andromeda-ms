package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class PatAzureAuthStrategy implements AzureAuthStrategy {

    private final EncryptionService encryptionService;

    @Override
    public String getAuthorizationHeader(AzureConnection connection) {
        String decryptedPat = encryptionService.decrypt(connection.getPersonalAccessToken());
        String encoded = Base64.getEncoder()
                .encodeToString((":" + decryptedPat).getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}

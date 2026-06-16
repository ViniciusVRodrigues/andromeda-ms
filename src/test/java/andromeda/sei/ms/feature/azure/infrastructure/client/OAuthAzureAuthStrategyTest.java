package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAzureAuthStrategyTest {

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private OAuthAzureAuthStrategy strategy;

    @Test
    void getAuthorizationHeader_shouldReturnBearerHeaderFromDecryptedToken() {
        AzureConnection connection = new AzureConnection(
                "id", "org", "https://dev.azure.com/myorg",
                "encryptedToken", null, Instant.now().plusSeconds(600),
                "user-1", AzureConnectionStatus.CONNECTED,
                null, 0, null, null, true, Instant.now(), Instant.now(), null,
                AzureAuthMethod.OAUTH, null);

        when(encryptionService.decrypt("encryptedToken")).thenReturn("decryptedToken");

        String header = strategy.getAuthorizationHeader(connection);

        assertThat(header).isEqualTo("Bearer decryptedToken");
    }
}

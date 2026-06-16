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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatAzureAuthStrategyTest {

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private PatAzureAuthStrategy strategy;

    @Test
    void getAuthorizationHeader_shouldReturnBasicHeaderFromDecryptedPat() {
        AzureConnection connection = new AzureConnection(
                "id", "org", "https://dev.azure.com/myorg",
                null, null, null, null, AzureConnectionStatus.CONNECTED,
                null, 0, null, null, true, Instant.now(), Instant.now(), null,
                AzureAuthMethod.PAT, "encryptedPat");

        when(encryptionService.decrypt("encryptedPat")).thenReturn("mySecretPat");

        String header = strategy.getAuthorizationHeader(connection);

        String expectedEncoded = Base64.getEncoder()
                .encodeToString(":mySecretPat".getBytes(StandardCharsets.UTF_8));
        assertThat(header).isEqualTo("Basic " + expectedEncoded);
    }
}

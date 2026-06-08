package andromeda.sei.ms.feature.azure.infrastructure.security;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AzureTokenRefreshServiceTest {

    @Mock
    private AzureConnectionRepository azureConnectionRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private WebClient azureWebClient;

    private AzureTokenRefreshService service;

    @BeforeEach
    void setUp() {
        service = new AzureTokenRefreshService(azureConnectionRepository, encryptionService, azureWebClient);
        ReflectionTestUtils.setField(service, "clientId", "test-client-id");
        ReflectionTestUtils.setField(service, "clientSecret", "test-client-secret");
    }

    @Test
    void shouldNotRefreshWhenTokenIsStillValid() {
        var connection = connectionWithExpiry(Instant.now().plusSeconds(3600));

        var result = service.refreshIfNeeded(connection);

        assertThat(result).isSameAs(connection);
        verify(azureWebClient, never()).post();
    }

    @Test
    void shouldSetStatusExpiredWhenWebClientThrows() {
        var connection = connectionWithExpiry(Instant.now().minusSeconds(60));

        // WebClient.post() throws immediately when mocked with no stubs configured
        // (Mockito returns null for unstubbed calls on Mocks — WebClient chain will NPE)
        // We verify the error-handling branch sets status to EXPIRED.
        try {
            service.refreshIfNeeded(connection);
        } catch (RuntimeException e) {
            // expected — NPE from unmocked WebClient chain triggers error handler
        }

        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.EXPIRED);
        verify(azureConnectionRepository).save(connection);
    }

    private AzureConnection connectionWithExpiry(Instant expiresAt) {
        return new AzureConnection(
                "conn-1", "org-1", "https://dev.azure.com/myorg",
                "enc-access", "enc-refresh", expiresAt,
                "user-1", AzureConnectionStatus.CONNECTED,
                null, 0, null, null, true, Instant.now(), Instant.now(), null);
    }
}

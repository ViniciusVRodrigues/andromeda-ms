package andromeda.sei.ms.feature.azure.infrastructure.security;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AzureTokenRefreshService {

    private static final String TOKEN_ENDPOINT =
            "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final int REFRESH_LOOKAHEAD_MINUTES = 10;

    private final AzureConnectionRepository azureConnectionRepository;
    private final EncryptionService encryptionService;
    private final WebClient azureWebClient;

    @Value("${azure.client-id}")
    private String clientId;

    @Value("${azure.client-secret}")
    private String clientSecret;

    public AzureConnection refreshIfNeeded(AzureConnection connection) {
        Instant threshold = Instant.now().plus(REFRESH_LOOKAHEAD_MINUTES, ChronoUnit.MINUTES);
        if (connection.getTokenExpiresAt() != null && connection.getTokenExpiresAt().isAfter(threshold)) {
            return connection;
        }

        try {
            String refreshToken = encryptionService.decrypt(connection.getRefreshToken());

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = azureWebClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                            .with("refresh_token", refreshToken)
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("scope", "offline_access 499b84ac-1321-427f-aa17-267ca6975798/user_impersonation"))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String newAccessToken = (String) tokenResponse.get("access_token");
            String newRefreshToken = (String) tokenResponse.getOrDefault("refresh_token",
                    encryptionService.decrypt(connection.getRefreshToken()));
            Number expiresIn = (Number) tokenResponse.getOrDefault("expires_in", 3600);

            connection.setAccessToken(encryptionService.encrypt(newAccessToken));
            connection.setRefreshToken(encryptionService.encrypt(newRefreshToken));
            connection.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn.longValue()));
            connection.setStatus(AzureConnectionStatus.CONNECTED);

            return azureConnectionRepository.save(connection);
        } catch (Exception e) {
            connection.setStatus(AzureConnectionStatus.EXPIRED);
            azureConnectionRepository.save(connection);
            throw new RuntimeException("AZURE_REFRESH_TOKEN_EXPIRED: Failed to refresh token for org "
                    + connection.getOrganizationId(), e);
        }
    }
}

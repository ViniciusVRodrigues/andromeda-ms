package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenInput;
import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenOutput;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GenerateConnectorTokenUseCase {

    private final ConnectorTokenRepository connectorTokenRepository;

    public ConnectorTokenOutput execute(ConnectorTokenInput input) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);
        String label = "token-" + rawToken.substring(0, 8);

        Instant now = Instant.now();
        ConnectorToken token = new ConnectorToken(
                null,
                input.organizationId(),
                input.userId(),
                label,
                tokenHash,
                true,
                0L,
                now,
                now
        );

        ConnectorToken saved = connectorTokenRepository.save(token);
        return ConnectorTokenOutput.fromWithRawToken(saved, rawToken);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenRevokedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RevokeConnectorTokenUseCase {

    private final ConnectorTokenRepository connectorTokenRepository;

    public void execute(String tokenId, String organizationId) {
        ConnectorToken token = connectorTokenRepository
                .findByIdAndOrganizationId(tokenId, organizationId)
                .orElseThrow(() -> new ConnectorTokenNotFoundException(tokenId));

        if (!token.isActive()) {
            throw new ConnectorTokenRevokedException(tokenId);
        }

        token.revoke();
        connectorTokenRepository.save(token);
    }
}

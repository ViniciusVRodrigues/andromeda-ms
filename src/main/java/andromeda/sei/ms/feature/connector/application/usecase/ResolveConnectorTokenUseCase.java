package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenRevokedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResolveConnectorTokenUseCase {

    private final ConnectorTokenRepository connectorTokenRepository;

    public ConnectorToken execute(String organizationId, String tokenHash) {
        ConnectorToken token = connectorTokenRepository
                .findByOrganizationIdAndTokenHash(organizationId, tokenHash)
                .orElseThrow(() -> new ConnectorTokenNotFoundException(tokenHash.substring(0, 8)));

        if (!token.isActive()) {
            throw new ConnectorTokenRevokedException(token.getId());
        }

        return token;
    }
}

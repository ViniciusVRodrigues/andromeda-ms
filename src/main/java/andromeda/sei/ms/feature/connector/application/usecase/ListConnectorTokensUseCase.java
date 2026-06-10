package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenOutput;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListConnectorTokensUseCase {

    private final ConnectorTokenRepository connectorTokenRepository;

    public List<ConnectorTokenOutput> execute(String organizationId, String userId) {
        return connectorTokenRepository
                .findActiveByOrganizationIdAndUserId(organizationId, userId)
                .stream()
                .map(ConnectorTokenOutput::from)
                .toList();
    }
}

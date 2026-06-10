package andromeda.sei.ms.feature.connector.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class ConnectorTokenNotFoundException extends ResourceNotFoundException {

    public ConnectorTokenNotFoundException(String id) {
        super("CONNECTOR_TOKEN_NOT_FOUND", "Connector token not found: " + id);
    }
}

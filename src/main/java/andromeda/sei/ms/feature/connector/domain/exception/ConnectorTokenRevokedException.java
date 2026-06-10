package andromeda.sei.ms.feature.connector.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class ConnectorTokenRevokedException extends BusinessRuleException {

    public ConnectorTokenRevokedException(String id) {
        super("CONNECTOR_TOKEN_REVOKED", "Connector token has been revoked: " + id);
    }
}

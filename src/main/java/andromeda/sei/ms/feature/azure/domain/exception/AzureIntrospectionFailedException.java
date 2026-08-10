package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzureIntrospectionFailedException extends BusinessRuleException {

    public AzureIntrospectionFailedException(String message) {
        super("AZURE_INTROSPECTION_FAILED", message);
    }
}

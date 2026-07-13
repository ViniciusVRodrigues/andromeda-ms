package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzureOrgUrlInvalidException extends BusinessRuleException {

    public AzureOrgUrlInvalidException(String message) {
        super("AZURE_ORG_URL_INVALID", message);
    }
}

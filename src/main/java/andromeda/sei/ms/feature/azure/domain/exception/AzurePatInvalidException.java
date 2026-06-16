package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzurePatInvalidException extends BusinessRuleException {

    public AzurePatInvalidException(String message) {
        super("AZURE_PAT_INVALID", message);
    }
}

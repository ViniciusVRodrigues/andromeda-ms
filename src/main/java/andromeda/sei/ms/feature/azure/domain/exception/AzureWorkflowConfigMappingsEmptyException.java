package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzureWorkflowConfigMappingsEmptyException extends BusinessRuleException {

    public AzureWorkflowConfigMappingsEmptyException(String message) {
        super("AZURE_WORKFLOW_CONFIG_MAPPINGS_EMPTY", message);
    }
}

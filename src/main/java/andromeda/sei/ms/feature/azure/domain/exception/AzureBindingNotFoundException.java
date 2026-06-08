package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class AzureBindingNotFoundException extends ResourceNotFoundException {

    public AzureBindingNotFoundException(String projectId) {
        super("AZURE_BINDING_NOT_FOUND", "No active Azure DevOps binding for project: " + projectId);
    }
}

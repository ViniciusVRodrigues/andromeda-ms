package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class AzureConnectionNotFoundException extends ResourceNotFoundException {

    public AzureConnectionNotFoundException(String organizationId) {
        super("AZURE_CONNECTION_NOT_FOUND", "No active Azure DevOps connection for organization: " + organizationId);
    }
}

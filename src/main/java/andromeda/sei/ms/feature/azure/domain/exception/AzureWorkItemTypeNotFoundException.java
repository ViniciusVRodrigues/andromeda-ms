package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class AzureWorkItemTypeNotFoundException extends ResourceNotFoundException {

    public AzureWorkItemTypeNotFoundException(String typeName) {
        super("AZURE_WORK_ITEM_TYPE_NOT_FOUND", "Work item type not found on bound project: " + typeName);
    }
}

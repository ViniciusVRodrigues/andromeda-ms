package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzureHierarchyLevelDuplicateTypeException extends BusinessRuleException {

    public AzureHierarchyLevelDuplicateTypeException(String rawType) {
        super("AZURE_HIERARCHY_LEVEL_DUPLICATE_TYPE",
                "Duplicate hierarchy level entry for raw type: " + rawType);
    }
}

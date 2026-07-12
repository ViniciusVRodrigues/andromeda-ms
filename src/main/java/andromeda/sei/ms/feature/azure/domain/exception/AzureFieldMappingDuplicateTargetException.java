package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzureFieldMappingDuplicateTargetException extends BusinessRuleException {

    public AzureFieldMappingDuplicateTargetException(String internalField) {
        super("AZURE_FIELD_MAPPING_DUPLICATE_TARGET",
                "Duplicate field mapping for internal field: " + internalField);
    }
}

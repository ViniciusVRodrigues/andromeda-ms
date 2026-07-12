package andromeda.sei.ms.feature.azure.domain;

public class FieldMapping {

    public enum InternalFieldTarget { COMPLEXITY, EFFORT }

    private InternalFieldTarget internalField;
    private String azureFieldReferenceName;
    private String azureFieldType;

    public FieldMapping(InternalFieldTarget internalField, String azureFieldReferenceName, String azureFieldType) {
        this.internalField = internalField;
        this.azureFieldReferenceName = azureFieldReferenceName;
        this.azureFieldType = azureFieldType;
    }

    public InternalFieldTarget getInternalField() { return internalField; }
    public void setInternalField(InternalFieldTarget internalField) { this.internalField = internalField; }

    public String getAzureFieldReferenceName() { return azureFieldReferenceName; }
    public void setAzureFieldReferenceName(String azureFieldReferenceName) { this.azureFieldReferenceName = azureFieldReferenceName; }

    public String getAzureFieldType() { return azureFieldType; }
    public void setAzureFieldType(String azureFieldType) { this.azureFieldType = azureFieldType; }
}

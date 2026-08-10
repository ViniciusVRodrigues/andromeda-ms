package andromeda.sei.ms.feature.azure.domain;

public class FieldMapping {

    public enum InternalFieldTarget { COMPLEXITY, EFFORT }

    private InternalFieldTarget internalField;
    private String workItemType;
    private String azureFieldReferenceName;
    private String azureFieldType;

    /**
     * @deprecated use {@link #FieldMapping(InternalFieldTarget, String, String, String)}; kept for
     * callers/tests that predate per-work-item-type field mapping. Constructs a wildcard entry
     * ({@code workItemType == null}, applies to all work item types).
     */
    @Deprecated
    public FieldMapping(InternalFieldTarget internalField, String azureFieldReferenceName, String azureFieldType) {
        this(internalField, null, azureFieldReferenceName, azureFieldType);
    }

    /**
     * @param workItemType the raw Azure work item type this field mapping applies to. {@code null}
     *                      is the legacy-compatibility sentinel meaning "applies to all work item
     *                      types" — mirrors {@link StatusMapping#getWorkItemType()}.
     */
    public FieldMapping(InternalFieldTarget internalField, String workItemType,
                         String azureFieldReferenceName, String azureFieldType) {
        this.internalField = internalField;
        this.workItemType = workItemType;
        this.azureFieldReferenceName = azureFieldReferenceName;
        this.azureFieldType = azureFieldType;
    }

    public InternalFieldTarget getInternalField() { return internalField; }
    public void setInternalField(InternalFieldTarget internalField) { this.internalField = internalField; }

    public String getWorkItemType() { return workItemType; }
    public void setWorkItemType(String workItemType) { this.workItemType = workItemType; }

    public String getAzureFieldReferenceName() { return azureFieldReferenceName; }
    public void setAzureFieldReferenceName(String azureFieldReferenceName) { this.azureFieldReferenceName = azureFieldReferenceName; }

    public String getAzureFieldType() { return azureFieldType; }
    public void setAzureFieldType(String azureFieldType) { this.azureFieldType = azureFieldType; }
}

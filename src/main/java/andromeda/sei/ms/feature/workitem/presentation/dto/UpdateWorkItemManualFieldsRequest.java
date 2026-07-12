package andromeda.sei.ms.feature.workitem.presentation.dto;

/**
 * complexity/effort are only applied when their corresponding JSON key was present in the
 * request body — presence is detected via Jackson's @JsonProperty-driven binding combined
 * with explicit clear flags, so "omitted" (leave unchanged) is distinguishable from
 * "explicitly cleared back to the Azure-sync-managed value".
 */
public record UpdateWorkItemManualFieldsRequest(
        Double complexity,
        boolean clearComplexityOverride,
        Double effort,
        boolean clearEffortOverride
) {}

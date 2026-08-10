package andromeda.sei.ms.feature.workitem.application.dto;

/**
 * complexityPresent/effortPresent distinguish "field present in the request body" from
 * "field omitted" per the manual-override contract: only present fields are updated,
 * and an explicit clear flag (independent of the value) reverts the field to AZURE_SYNC.
 */
public record UpdateWorkItemManualFieldsInput(
        boolean complexityPresent,
        Double complexity,
        boolean clearComplexityOverride,
        boolean effortPresent,
        Double effort,
        boolean clearEffortOverride
) {}

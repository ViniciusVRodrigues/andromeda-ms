package andromeda.sei.ms.feature.workitem.presentation.dto;

public record WorkItemManualFieldsResponse(
        String id,
        Double complexity,
        String complexitySource,
        Double effort,
        String effortSource
) {}

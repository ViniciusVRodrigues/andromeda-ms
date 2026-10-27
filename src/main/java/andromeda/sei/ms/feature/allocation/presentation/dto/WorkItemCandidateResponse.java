package andromeda.sei.ms.feature.allocation.presentation.dto;

public record WorkItemCandidateResponse(
        String workItemId,
        String title,
        String sprintName,
        String type,
        String externalId,
        Double complexity,
        Double effort
) {}

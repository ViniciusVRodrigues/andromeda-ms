package andromeda.sei.ms.feature.allocation.application.dto;

public record WorkItemCandidateOutput(
        String workItemId,
        String title,
        String sprintName,
        String type,
        String externalId,
        Double complexity,
        Double effort
) {}

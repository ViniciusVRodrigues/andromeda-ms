package andromeda.sei.ms.feature.allocation.presentation.dto;

public record SprintItemResponse(String workItemId, String title, String sprintName,
        String type, String externalId, Double storyPoints) {}

package andromeda.sei.ms.feature.azure.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record BindAzureProjectRequest(
        @NotBlank String azureProjectId,
        @NotBlank String azureProjectName
) {}

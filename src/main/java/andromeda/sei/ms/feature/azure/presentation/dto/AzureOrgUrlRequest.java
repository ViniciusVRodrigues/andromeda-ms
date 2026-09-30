package andromeda.sei.ms.feature.azure.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** New organization URL of the Azure DevOps connection. */
public record AzureOrgUrlRequest(@NotBlank String azureOrgUrl) {}

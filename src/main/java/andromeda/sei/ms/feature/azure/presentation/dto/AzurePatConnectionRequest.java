package andromeda.sei.ms.feature.azure.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record AzurePatConnectionRequest(
        @NotBlank String adoOrganizationUrl,
        @NotBlank String personalAccessToken
) {}

package andromeda.sei.ms.feature.azure.application.dto;

import jakarta.validation.constraints.NotBlank;

public record AzurePatConnectionInput(
        @NotBlank String adoOrganizationUrl,
        @NotBlank String personalAccessToken
) {}

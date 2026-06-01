package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OnboardOrganizationRequest(
        @NotBlank String name,
        @NotBlank @Email String adminEmail,
        @NotBlank String adminName,
        @NotBlank String adminPassword
) {
}

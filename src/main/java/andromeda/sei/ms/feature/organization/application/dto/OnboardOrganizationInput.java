package andromeda.sei.ms.feature.organization.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OnboardOrganizationInput(
        @NotBlank String name,
        @NotBlank @Email String adminEmail,
        @NotBlank String adminName,
        @NotBlank String adminPassword
) {
}

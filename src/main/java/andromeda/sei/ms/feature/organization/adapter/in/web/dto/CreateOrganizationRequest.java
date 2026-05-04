package andromeda.sei.ms.feature.organization.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(
        @NotBlank String organizationName,
        @NotBlank String adminName,
        @NotBlank @Email String adminEmail,
        @NotBlank String adminPassword
) {}

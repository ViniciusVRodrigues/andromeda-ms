package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteOrganizationRequest(@NotBlank String confirmSlug) {}

package andromeda.sei.ms.feature.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
        @NotBlank String name,
        @NotBlank String description
) {
}

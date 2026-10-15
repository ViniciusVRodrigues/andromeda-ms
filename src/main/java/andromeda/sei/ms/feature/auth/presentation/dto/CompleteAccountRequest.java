package andromeda.sei.ms.feature.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteAccountRequest(@NotBlank String token, @NotBlank String name, @NotBlank String password,
                                     String funnelId) {
}

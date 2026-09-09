package andromeda.sei.ms.feature.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPasswordResetRequest(
        @NotBlank String token,
        @NotBlank String password
) {}

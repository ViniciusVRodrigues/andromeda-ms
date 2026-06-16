package andromeda.sei.ms.feature.auth.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmailVerificationRequest(
        @NotBlank @Email String email
) {}

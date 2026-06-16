package andromeda.sei.ms.feature.user.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteUserRequest(
        @NotBlank @Email String email,
        @NotBlank String roleName,
        String seniority
) {}

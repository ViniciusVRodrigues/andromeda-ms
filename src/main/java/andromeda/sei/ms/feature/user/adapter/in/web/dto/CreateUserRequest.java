package andromeda.sei.ms.feature.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull UUID roleId
) {}

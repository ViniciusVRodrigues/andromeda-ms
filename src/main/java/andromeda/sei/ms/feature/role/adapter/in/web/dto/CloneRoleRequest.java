package andromeda.sei.ms.feature.role.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CloneRoleRequest(
        @NotNull UUID sourceRoleId,
        @NotBlank String newName
) {}

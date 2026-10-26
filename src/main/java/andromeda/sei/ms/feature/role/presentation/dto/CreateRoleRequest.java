package andromeda.sei.ms.feature.role.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoleRequest(@NotBlank @Size(max = 60) String name, List<String> permissionCodes) {
}

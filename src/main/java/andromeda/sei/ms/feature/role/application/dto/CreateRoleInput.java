package andromeda.sei.ms.feature.role.application.dto;

import java.util.List;

public record CreateRoleInput(String name, List<String> permissionCodes) {
}

package andromeda.sei.ms.feature.role.application.dto;

import java.util.List;

public record UpdateRolePermissionsInput(String roleId, List<String> add, List<String> remove) {
}

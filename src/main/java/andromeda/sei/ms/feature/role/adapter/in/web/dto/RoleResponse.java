package andromeda.sei.ms.feature.role.adapter.in.web.dto;

import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.Role;
import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, boolean isDefault, List<String> permissions) {
    public static RoleResponse from(Role role) {
        List<String> perms = role.permissions() == null ? List.of() :
                role.permissions().stream().map(Permission::code).toList();
        return new RoleResponse(role.id(), role.name(), role.isDefault(), perms);
    }
}

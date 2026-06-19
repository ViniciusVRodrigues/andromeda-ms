package andromeda.sei.ms.feature.role.application.dto;

import andromeda.sei.ms.feature.role.domain.Role;

import java.util.List;

public record RoleOutput(
        String id,
        String organizationId,
        String name,
        String label,
        boolean isDefault,
        List<String> permissionCodes
) {
    public static RoleOutput from(Role role) {
        return new RoleOutput(
                role.getId(),
                role.getOrganizationId(),
                role.getName(),
                role.getLabel(),
                role.isDefault(),
                role.getPermissionCodes()
        );
    }
}

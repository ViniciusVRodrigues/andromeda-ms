package andromeda.sei.ms.feature.role.presentation.dto;

import java.util.List;

public record RoleResponse(
        String id,
        String name,
        String label,
        String organizationId,
        boolean isDefault,
        List<String> permissionCodes
) {
}

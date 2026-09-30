package andromeda.sei.ms.feature.user.application.dto;

import java.util.List;
import java.util.Map;

/**
 * Role preset, overrides and effective permissions of a user (project-scoped-permissions,
 * user-permission-overrides). {@code projectPermissions} maps every project accessible to the user
 * to its effective project-scopable codes.
 */
public record UserPermissionsOutput(
        String userId,
        String roleId,
        String roleName,
        List<String> rolePermissions,
        List<PermissionOverrideOutput> overrides,
        List<String> orgPermissions,
        Map<String, List<String>> projectPermissions,
        Long version
) {}

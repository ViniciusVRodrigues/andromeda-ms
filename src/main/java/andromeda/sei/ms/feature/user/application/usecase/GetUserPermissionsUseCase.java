package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.application.dto.PermissionOverrideOutput;
import andromeda.sei.ms.feature.user.application.dto.UserPermissionsOutput;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Role preset, overrides and effective permissions of a user (project-scoped-permissions 5.1).
 * Read fresh, not from the cache, because the returned {@code version} feeds the optimistic write.
 */
@Component
@RequiredArgsConstructor
public class GetUserPermissionsUseCase {

    private final EffectivePermissionService effectivePermissionService;

    public UserPermissionsOutput execute(String organizationId, String userId) {
        UserPermissionSnapshot snapshot = effectivePermissionService.reload(organizationId, userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return new UserPermissionsOutput(
                snapshot.userId(),
                snapshot.roleId(),
                snapshot.roleName(),
                snapshot.roleCodes(),
                snapshot.overrides().stream().map(PermissionOverrideOutput::from).toList(),
                effectivePermissionService.orgPermissions(snapshot),
                effectivePermissionService.projectPermissions(snapshot),
                snapshot.userVersion());
    }
}

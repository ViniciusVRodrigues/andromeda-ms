package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.application.security.ProjectMembersChanged;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import andromeda.sei.ms.shared.application.security.UserPermissionsChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Invalidates cached effective permissions when their inputs change (project-scoped-permissions
 * D5). Synchronous: the change is visible to the very next request, and to the response of the
 * use case that published it.
 *
 * <p>A role or membership change touches many users at once; the whole cache is dropped — it is
 * rebuilt lazily, one cheap lookup per user.
 */
@Component
@RequiredArgsConstructor
public class PermissionCacheInvalidationListener {

    private final EffectivePermissionService effectivePermissionService;

    @EventListener
    public void on(UserPermissionsChanged event) {
        effectivePermissionService.evictUser(event.organizationId(), event.userId());
    }

    @EventListener
    public void on(RoleChanged event) {
        effectivePermissionService.evictAll();
    }

    @EventListener
    public void on(ProjectMembersChanged event) {
        effectivePermissionService.evictAll();
    }
}

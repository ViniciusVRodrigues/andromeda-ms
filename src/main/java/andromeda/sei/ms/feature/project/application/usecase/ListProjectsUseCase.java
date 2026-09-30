package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Projects visible to the caller (project-scoped-permissions 7.1, project-member-management):
 * every active project with {@code PROJECT_ACCESS_ALL}; otherwise the ones they are a member of
 * plus the ones where they have a project-level grant. {@code USER_MANAGE} is no longer the
 * criterion. With {@code permissionCode}, only the projects where that code is effective — the
 * dashboards' project selectors.
 */
@Component
@RequiredArgsConstructor
public class ListProjectsUseCase {

    private final EffectivePermissionService effectivePermissionService;

    public List<Project> execute(UserPermissionSnapshot caller, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return effectivePermissionService.accessibleProjects(caller);
        }
        if (!PermissionCatalog.isKnown(permissionCode)) {
            throw new BusinessRuleException("PERMISSION_CODE_INVALID", "Unknown permission code: " + permissionCode);
        }
        return effectivePermissionService.projectsWith(caller, permissionCode);
    }
}

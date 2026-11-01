package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Who may decide an approval (uso-ia-fora-de-entrega, design 2.3): a caller with AI_ALLOCATION_APPROVE on
 * one of the allocation's {@code approverProjectIds}; with none listed, a caller holding it at organization
 * level. The permission is project-scopable, so the listing resolves the projects once per request.
 */
@Component
@RequiredArgsConstructor
public class AllocationApproverAccess {

    private final EffectivePermissionService effectivePermissionService;

    /** Projects where the caller effectively holds AI_ALLOCATION_APPROVE. */
    public Set<String> approverProjects(UserPermissionSnapshot caller) {
        return effectivePermissionService.projectIdsWith(caller, PermissionCatalog.AI_ALLOCATION_APPROVE);
    }

    public boolean canDecide(UserPermissionSnapshot caller, Set<String> callerProjects, AiUsageAllocation allocation) {
        List<String> approverProjectIds = allocation.getApproval() == null ? List.of()
                : allocation.getApproval().approverProjectIds();
        if (approverProjectIds.isEmpty()) {
            return caller.resolver().hasOrg(PermissionCatalog.AI_ALLOCATION_APPROVE);
        }
        return approverProjectIds.stream().anyMatch(callerProjects::contains);
    }
}

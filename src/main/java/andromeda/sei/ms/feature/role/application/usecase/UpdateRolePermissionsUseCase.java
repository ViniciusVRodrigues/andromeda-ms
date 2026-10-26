package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.application.dto.UpdateRolePermissionsInput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.DefaultRoleModificationException;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import andromeda.sei.ms.feature.role.domain.exception.RolePermissionNotAssignableException;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.PermissionAliasResolver;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateRolePermissionsUseCase {

    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoleOutput execute(UpdateRolePermissionsInput input, String organizationId) {
        Role role = roleRepository.findById(input.roleId())
                .orElseThrow(() -> new RoleNotFoundException(input.roleId()));

        if (role.getOrganizationId() != null && !role.getOrganizationId().equals(organizationId)) {
            throw new RoleNotFoundException(input.roleId());
        }

        if (role.isDefault()) {
            throw new DefaultRoleModificationException();
        }

        // Canonicalize BEFORE applying the edit: a role still carrying a GITHUB_* mirror would
        // otherwise resurrect REPOS_* after its removal. An old client sending GITHUB_* in add or
        // remove is normalized to the current code the same way. The legacy mirrors are re-derived
        // on write by RoleDocumentMapper (PermissionAliasResolver.withLegacyMirrors).
        Set<String> codes = new LinkedHashSet<>(PermissionAliasResolver.canonicalize(role.getPermissionCodes()));
        List<String> toAdd = PermissionAliasResolver.canonicalize(input.add());
        toAdd.stream().filter(c -> !PermissionCatalog.isAssignableToRole(c)).findFirst()
                .ifPresent(c -> { throw new RolePermissionNotAssignableException(c); });
        codes.addAll(toAdd);
        PermissionAliasResolver.canonicalize(input.remove()).forEach(codes::remove);
        codes.removeAll(PermissionCatalog.NON_ASSIGNABLE_TO_ROLES); // defense: never inherited by an organization role
        role.setPermissionCodes(new ArrayList<>(codes));

        Role saved = roleRepository.save(role);
        eventPublisher.publishEvent(new RoleChanged(saved.getId(), saved.getOrganizationId()));
        return RoleOutput.from(saved);
    }
}

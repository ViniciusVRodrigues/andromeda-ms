package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.application.dto.UpdateRolePermissionsInput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.DefaultRoleModificationException;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateRolePermissionsUseCase {

    private final RoleRepository roleRepository;

    public RoleOutput execute(UpdateRolePermissionsInput input, String organizationId) {
        Role role = roleRepository.findById(input.roleId())
                .orElseThrow(() -> new RoleNotFoundException(input.roleId()));

        if (role.getOrganizationId() != null && !role.getOrganizationId().equals(organizationId)) {
            throw new RoleNotFoundException(input.roleId());
        }

        if (role.isDefault()) {
            throw new DefaultRoleModificationException();
        }

        Set<String> codes = new LinkedHashSet<>(role.getPermissionCodes());
        if (input.add() != null) {
            codes.addAll(input.add());
        }
        if (input.remove() != null) {
            codes.removeAll(input.remove());
        }
        role.setPermissionCodes(new ArrayList<>(codes));

        Role saved = roleRepository.save(role);
        return RoleOutput.from(saved);
    }
}

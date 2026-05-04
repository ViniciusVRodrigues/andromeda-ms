package andromeda.sei.ms.feature.role.application.service;

import andromeda.sei.ms.feature.role.application.port.in.DeleteRoleUseCase;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.shared.exception.BusinessException;
import andromeda.sei.ms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteRoleService implements DeleteRoleUseCase {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void execute(UUID roleId, UUID organizationId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        if (role.isDefault()) {
            throw new BusinessException("Cannot delete a default role");
        }

        if (roleRepository.hasAssignedUsers(roleId)) {
            throw new BusinessException("Cannot delete a role that has assigned users");
        }

        roleRepository.delete(roleId);
    }
}

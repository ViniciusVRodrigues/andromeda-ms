package andromeda.sei.ms.feature.role.application.service;

import andromeda.sei.ms.feature.role.adapter.in.web.dto.UpdateRolePermissionsRequest;
import andromeda.sei.ms.feature.role.application.port.in.UpdateRolePermissionsUseCase;
import andromeda.sei.ms.feature.role.application.port.out.PermissionRepository;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.shared.exception.BusinessException;
import andromeda.sei.ms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateRolePermissionsService implements UpdateRolePermissionsUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public Role execute(UUID roleId, UpdateRolePermissionsRequest request, UUID organizationId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        if (role.isDefault()) {
            throw new BusinessException("Cannot modify permissions of a default role");
        }

        List<Permission> current = role.permissions() != null ? new ArrayList<>(role.permissions()) : new ArrayList<>();

        if (request.remove() != null && !request.remove().isEmpty()) {
            current.removeIf(p -> request.remove().contains(p.code()));
        }

        if (request.add() != null && !request.add().isEmpty()) {
            Set<String> existingCodes = current.stream().map(Permission::code).collect(Collectors.toSet());
            List<Permission> toAdd = permissionRepository.findByCodes(request.add()).stream()
                    .filter(p -> !existingCodes.contains(p.code()))
                    .toList();
            current.addAll(toAdd);
        }

        Role updated = new Role(role.id(), role.organizationId(), role.name(), role.isDefault(), role.clonedFromRoleId(), current);
        return roleRepository.save(updated);
    }
}

package andromeda.sei.ms.feature.role.application.service;

import andromeda.sei.ms.feature.role.adapter.in.web.dto.CloneRoleRequest;
import andromeda.sei.ms.feature.role.application.port.in.CloneRoleUseCase;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloneRoleService implements CloneRoleUseCase {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public Role execute(CloneRoleRequest request, UUID organizationId) {
        Role source = roleRepository.findById(request.sourceRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.sourceRoleId()));

        Role cloned = new Role(null, organizationId, request.newName(), false, source.id(), source.permissions());
        return roleRepository.save(cloned);
    }
}

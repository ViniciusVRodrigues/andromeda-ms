package andromeda.sei.ms.feature.role.application.service;

import andromeda.sei.ms.feature.role.application.port.in.ListRolesUseCase;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListRolesService implements ListRolesUseCase {

    private final RoleRepository roleRepository;

    @Override
    public List<Role> execute(UUID organizationId) {
        return roleRepository.findAvailableForOrganization(organizationId);
    }
}

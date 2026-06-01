package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListRolesUseCase {

    private final RoleRepository roleRepository;

    public List<RoleOutput> execute(String organizationId) {
        return roleRepository.findAvailableForOrganization(organizationId)
                .stream()
                .map(RoleOutput::from)
                .toList();
    }
}

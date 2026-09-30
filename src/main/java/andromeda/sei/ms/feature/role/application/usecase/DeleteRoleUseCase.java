package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.DefaultRoleModificationException;
import andromeda.sei.ms.feature.role.domain.exception.RoleInUseException;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteRoleUseCase {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(String roleId, String organizationId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (role.getOrganizationId() != null && !role.getOrganizationId().equals(organizationId)) {
            throw new RoleNotFoundException(roleId);
        }

        if (role.isDefault()) {
            throw new DefaultRoleModificationException();
        }

        if (userRepository.existsByOrganizationIdAndRoleIdAndActiveTrue(organizationId, roleId)) {
            throw new RoleInUseException(roleId);
        }

        role.setActive(false);
        roleRepository.save(role);
        eventPublisher.publishEvent(new RoleChanged(roleId, role.getOrganizationId()));
    }
}

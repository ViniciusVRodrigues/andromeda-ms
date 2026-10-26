package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.CloneRoleInput;
import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RoleNotFoundException;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class CloneRoleUseCase {

    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoleOutput execute(CloneRoleInput input, String organizationId) {
        if (input.sourceRoleId() == null) {
            throw new RoleNotFoundException("null");
        }
        Role source = roleRepository.findById(input.sourceRoleId())
                .filter(r -> r.getOrganizationId() == null || r.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new RoleNotFoundException(input.sourceRoleId()));
        String name = RoleNames.requireAvailable(input.newName(), organizationId, roleRepository);

        Instant now = Instant.now();
        Role cloned = new Role(
                null,
                organizationId,
                name,
                name,
                false,
                source.getId(),
                source.getPermissionCodes().stream().filter(PermissionCatalog::isAssignableToRole)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)),
                true,
                null,
                now,
                now
        );

        Role saved = roleRepository.save(cloned);
        eventPublisher.publishEvent(new RoleChanged(saved.getId(), saved.getOrganizationId()));
        return RoleOutput.from(saved);
    }
}

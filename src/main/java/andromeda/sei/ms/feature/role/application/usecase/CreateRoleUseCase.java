package andromeda.sei.ms.feature.role.application.usecase;

import andromeda.sei.ms.feature.role.application.dto.CreateRoleInput;
import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.role.domain.exception.RolePermissionNotAssignableException;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import andromeda.sei.ms.shared.domain.security.PermissionAliasResolver;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateRoleUseCase {

    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoleOutput execute(CreateRoleInput input, String organizationId) {
        String name = RoleNames.requireAvailable(input.name(), organizationId, roleRepository);

        List<String> requested = input.permissionCodes() == null ? List.of() : input.permissionCodes();
        // a null element would NPE inside canonicalize (List.copyOf): reject it as a client error
        if (requested.stream().anyMatch(java.util.Objects::isNull)) {
            throw new RolePermissionNotAssignableException("null");
        }
        List<String> codes = new ArrayList<>(new LinkedHashSet<>(PermissionAliasResolver.canonicalize(requested)));
        codes.stream().filter(c -> !PermissionCatalog.isKnown(c) || !PermissionCatalog.isAssignableToRole(c)).findFirst()
                .ifPresent(c -> { throw new RolePermissionNotAssignableException(c); });

        Instant now = Instant.now();
        Role saved = roleRepository.save(new Role(null, organizationId, name, name, false, null,
                codes, true, null, now, now));
        eventPublisher.publishEvent(new RoleChanged(saved.getId(), saved.getOrganizationId()));
        return RoleOutput.from(saved);
    }
}

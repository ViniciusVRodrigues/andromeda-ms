package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserInput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserOutput;
import andromeda.sei.ms.feature.auth.domain.exception.InvalidCredentialsException;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Profile of the caller with their effective permissions resolved on the server
 * (project-scoped-permissions 8.1): {@code orgPermissions} and {@code projectPermissions} (accessible
 * project to its effective project-scopable codes). {@code permissions} equals
 * {@code orgPermissions} for one release; the JWT claims are no longer echoed.
 */
@Component
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final EffectivePermissionService effectivePermissionService;
    private final RoleRepository roleRepository;

    public GetCurrentUserOutput execute(GetCurrentUserInput input) {
        User user = userRepository.findByIdAndOrganizationId(input.userId(), input.organizationId())
                .orElseThrow(InvalidCredentialsException::new);

        Organization org = organizationRepository.findById(input.organizationId())
                .orElseThrow(InvalidCredentialsException::new);

        UserPermissionSnapshot snapshot = effectivePermissionService
                .snapshot(input.organizationId(), input.userId())
                .orElseThrow(InvalidCredentialsException::new);
        List<String> orgPermissions = effectivePermissionService.orgPermissions(snapshot);

        /* Mesmo filtro do EffectivePermissionService: cargo global ou da própria organização. */
        Optional<Role> role = Optional.ofNullable(snapshot.roleId())
                .flatMap(roleRepository::findById)
                .filter(r -> r.getOrganizationId() == null || r.getOrganizationId().equals(input.organizationId()));
        String roleLabel = role
                .map(r -> r.getLabel() != null && !r.getLabel().isBlank() ? r.getLabel() : r.getName())
                .orElse(null);

        return new GetCurrentUserOutput(
                user.getId(),
                user.getName(),
                user.getEmail(),
                org.getId(),
                org.getName(),
                org.getSlug(),
                orgPermissions,
                orgPermissions,
                effectivePermissionService.projectPermissions(snapshot),
                role.map(Role::getId).orElse(null),
                roleLabel,
                user.getSeniority(),
                user.getCreatedAt()
        );
    }
}

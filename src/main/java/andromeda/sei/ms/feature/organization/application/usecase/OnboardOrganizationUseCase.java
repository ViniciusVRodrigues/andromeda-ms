package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationInput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationSlugConflictException;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OnboardOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public OnboardOrganizationOutput execute(OnboardOrganizationInput input) {
        String slug = SlugUtils.toSlug(input.name());

        if (organizationRepository.existsBySlug(slug)) {
            throw new OrganizationSlugConflictException(slug);
        }

        Instant now = Instant.now();
        Organization org = new Organization(null, input.name(), slug, true, 0L, now, now);
        Organization savedOrg = organizationRepository.save(org);

        Role adminRole = roleRepository.findGlobalByName("ADMIN")
                .orElseThrow(() -> new BusinessRuleException("ADMIN_ROLE_NOT_FOUND",
                        "Default ADMIN role not found — ensure seed data has been applied"));

        String passwordHash = passwordHasher.hash(input.adminPassword());

        User adminUser = new User(
                null,
                savedOrg.getId(),
                input.adminEmail(),
                passwordHash,
                input.adminName(),
                adminRole.getId(),
                null,
                null,
                true,
                true,
                0L,
                now,
                now
        );
        User savedUser = userRepository.save(adminUser);

        return new OnboardOrganizationOutput(
                savedOrg.getId(),
                savedOrg.getName(),
                savedOrg.getSlug(),
                savedUser.getId(),
                savedUser.getEmail(),
                adminRole.getPermissionCodes()
        );
    }
}

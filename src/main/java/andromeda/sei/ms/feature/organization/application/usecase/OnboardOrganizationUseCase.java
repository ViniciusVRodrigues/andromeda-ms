package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationInput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationSlugConflictException;
import andromeda.sei.ms.feature.user.domain.exception.UserEmailConflictException;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.usage.application.usecase.LinkFunnelUseCase;
import andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.LoginMethod;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.port.EmailService;
import andromeda.sei.ms.shared.application.port.FrontendUrlResolver;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.util.SlugUtils;
import andromeda.sei.ms.shared.domain.security.PermissionAliasResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OnboardOrganizationUseCase {

    private static final Logger log = LoggerFactory.getLogger(OnboardOrganizationUseCase.class);

    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final FrontendUrlResolver frontendUrlResolver;
    private final RecordLoginUseCase recordLoginUseCase;
    private final LinkFunnelUseCase linkFunnelUseCase;

    public OnboardOrganizationOutput execute(OnboardOrganizationInput input) {
        String slug = SlugUtils.toSlug(input.org().name());

        if (organizationRepository.existsBySlug(slug)) {
            throw new OrganizationSlugConflictException(slug);
        }

        if (userRepository.existsByEmail(input.admin().email())) {
            throw new UserEmailConflictException(input.admin().email());
        }

        Instant now = Instant.now();

        // 1. Create Organization
        Organization org = new Organization(
                null,
                input.org().name(),
                slug,
                input.org().sector(),
                input.org().country(),
                input.seniorities(),
                null,
                null,
                true,
                null,
                now,
                now
        );
        Organization savedOrg = organizationRepository.save(org);

        // 2. Create org-scoped roles from provided data
        List<Role> savedRoles = new ArrayList<>();
        for (OnboardOrganizationInput.RoleData roleData : input.roles()) {
            Role role = new Role(
                    null,
                    savedOrg.getId(),
                    roleData.name(),
                    roleData.label() != null ? roleData.label() : roleData.name(),
                    false,
                    null,
                    withProjectAccessAllForManagers(PermissionAliasResolver.canonicalize(roleData.permissionCodes())),
                    true,
                    null,
                    now,
                    now
            );
            savedRoles.add(roleRepository.save(role));
        }

        // 3. Resolve global ADMIN role and create admin user
        Role adminRole = roleRepository.findGlobalByName("ADMIN")
                .orElseThrow(() -> new BusinessRuleException("ADMIN_ROLE_NOT_FOUND",
                        "Default ADMIN role not found — ensure seed data has been applied"));

        String passwordHash = passwordHasher.hash(input.admin().password());

        User adminUser = new User(
                null,
                savedOrg.getId(),
                input.admin().email(),
                passwordHash,
                input.admin().name(),
                adminRole.getId(),
                null,
                null,
                null,
                null,
                null,
                true,
                true,
                null,
                now,
                now
        );
        User savedAdmin = userRepository.save(adminUser);

        // 4. Create first project
        String projectSlug = SlugUtils.toSlug(input.project().name());
        Project project = new Project(
                null,
                savedOrg.getId(),
                input.project().name(),
                projectSlug,
                input.project().description(),
                true,
                null,
                null,
                now,
                now
        );
        Project savedProject = projectRepository.save(project);

        // 5. Create invited users (confirmed=false, pending invite)
        // Build lookup from org-scoped roles first, then fall back to global defaults
        List<Role> globalDefaults = roleRepository.findAllGlobal();
        Map<String, String> roleIdByName = new java.util.HashMap<>();
        globalDefaults.forEach(r -> {
            roleIdByName.put(r.getName(), r.getId());
            roleIdByName.put(r.getLabel(), r.getId());
        });
        savedRoles.forEach(r -> {
            roleIdByName.put(r.getName(), r.getId());
            roleIdByName.put(r.getLabel(), r.getId());
        });

        int inviteCount = 0;
        if (input.invites() != null) {
            for (OnboardOrganizationInput.InviteData invite : input.invites()) {
                String inviteRoleId = roleIdByName.get(invite.roleName());
                if (inviteRoleId == null) {
                    throw new BusinessRuleException("ROLE_NOT_FOUND_FOR_INVITE",
                            "Role not found for invite: " + invite.roleName());
                }
                User invitedUser = new User(
                        null,
                        savedOrg.getId(),
                        invite.email(),
                        null,
                        null,
                        inviteRoleId,
                        invite.seniority(),
                        null,
                        null,
                        null,
                        UUID.randomUUID().toString(),
                        false,
                        true,
                        null,
                        now,
                        now
                );
                User savedInvite = userRepository.save(invitedUser);
                inviteCount++;
                try {
                    emailService.sendInvite(invite.email(), null, frontendUrlResolver.resolve() + "/onboard?token=" + savedInvite.getInviteToken());
                } catch (Exception e) {
                    log.warn("Failed to send invite email to {}: {}", invite.email(), e.getMessage());
                }
            }
        }

        /* The wizard hands the new admin a JWT right away: that is a login (best-effort). */
        try {
            recordLoginUseCase.execute(savedAdmin.getId(), savedOrg.getId(), LoginMethod.SIGNUP);
        } catch (RuntimeException e) {
            log.warn("Signup-login telemetry failed: {}", e.getClass().getSimpleName());
        }
        try {
            linkFunnelUseCase.execute(input.funnelId(), FunnelFlow.SIGNUP, savedOrg.getId(), savedAdmin.getId());
        } catch (RuntimeException e) {
            log.warn("Signup-funnel link failed: {}", e.getClass().getSimpleName());
        }

        return new OnboardOrganizationOutput(
                savedOrg.getId(),
                savedOrg.getName(),
                savedOrg.getSlug(),
                savedAdmin.getId(),
                savedAdmin.getEmail(),
                savedProject.getId(),
                savedProject.getName(),
                inviteCount,
                adminRole.getPermissionCodes()
        );
    }

    /**
     * Same rule as {@code V__project_access_all} (project-scoped-permissions D8): a role that manages
     * people sees every project, whether the organization was created before or after the migration.
     */
    /**
     * A role holding {@code USER_MANAGE} also gets {@code PROJECT_ACCESS_ALL} and {@code PROJECT_MANAGE}:
     * the same rule {@code ProjectAccessAllMigration} and {@code ProjectManageMigration} apply to
     * existing roles, so a new organization behaves like a migrated one.
     */
    private static List<String> withProjectAccessAllForManagers(List<String> codes) {
        List<String> result = new ArrayList<>(codes);
        if (!result.contains(PermissionCatalog.USER_MANAGE)) return result;
        for (String implied : List.of(PermissionCatalog.PROJECT_ACCESS_ALL, PermissionCatalog.PROJECT_MANAGE)) {
            if (!result.contains(implied)) result.add(implied);
        }
        return result;
    }
}

package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserInput;
import andromeda.sei.ms.feature.auth.application.dto.GetCurrentUserOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.infrastructure.security.CaffeinePermissionSnapshotCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * project-scoped-permissions 8.1 — {@code /auth/me} exposes {@code orgPermissions} and
 * {@code projectPermissions}; {@code permissions} equals {@code orgPermissions}.
 */
@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ProjectRepository projectRepository;

    @Test
    void should_exposeProjectPermissions_withProjectDenyApplied() {
        Project a = new Project("A", ORG, "A", "a", null, true, List.of("dev"), 1L, T, T);
        Project b = new Project("B", ORG, "B", "b", null, true, List.of("dev"), 1L, T, T);
        User dev = new User("dev", ORG, "dev@x.com", null, "Dev", "role-dev", null, null, null, null, null,
                true, true, 1L, T, T);
        dev.setPermissionOverrides(List.of(new PermissionOverride("REPOS_READ", PermissionEffect.DENY, "B", "admin", T)));
        when(userRepository.findByIdAndOrganizationId("dev", ORG)).thenReturn(Optional.of(dev));
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(new Organization(ORG, "Org", "org", null,
                null, List.of(), List.of(), null, true, 1L, T, T)));
        when(roleRepository.findById("role-dev")).thenReturn(Optional.of(new Role("role-dev", null, "DEVELOPER", "Dev",
                true, null, new ArrayList<>(List.of("REPOS_READ", "DASHBOARD_INDIVIDUAL")), true, 1L, T, T)));
        when(projectRepository.findAllActiveByOrganizationIdAndMemberId(ORG, "dev")).thenReturn(List.of(a, b));
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(a, b));
        EffectivePermissionService service = new EffectivePermissionService(userRepository, roleRepository,
                projectRepository, new CaffeinePermissionSnapshotCache(Duration.ofMinutes(5)));

        GetCurrentUserOutput output = new GetCurrentUserUseCase(userRepository, organizationRepository, service,
                roleRepository).execute(new GetCurrentUserInput("dev", ORG, List.of("USER_MANAGE")));

        assertThat(output.orgPermissions()).containsExactly("DASHBOARD_INDIVIDUAL", "REPOS_READ");
        assertThat(output.permissions()).isEqualTo(output.orgPermissions());
        assertThat(output.projectPermissions().get("A")).contains("REPOS_READ");
        assertThat(output.projectPermissions().get("B")).doesNotContain("REPOS_READ");
    }

    @Test
    void should_exposeRoleLabelSeniorityAndMemberSince() {
        Instant joined = Instant.parse("2026-03-10T12:00:00Z");
        User dev = new User("dev", ORG, "dev@x.com", null, "Dev", "role-dev", "PLENO", null, null, null, null,
                true, true, 1L, joined, T);
        when(userRepository.findByIdAndOrganizationId("dev", ORG)).thenReturn(Optional.of(dev));
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(new Organization(ORG, "Org", "org", null,
                null, List.of(), List.of(), null, true, 1L, T, T)));
        when(roleRepository.findById("role-dev")).thenReturn(Optional.of(new Role("role-dev", null, "DEVELOPER",
                "Desenvolvedor", true, null, new ArrayList<>(List.of("DASHBOARD_INDIVIDUAL")), true, 1L, T, T)));
        EffectivePermissionService service = new EffectivePermissionService(userRepository, roleRepository,
                projectRepository, new CaffeinePermissionSnapshotCache(Duration.ofMinutes(5)));

        GetCurrentUserOutput output = new GetCurrentUserUseCase(userRepository, organizationRepository, service,
                roleRepository).execute(new GetCurrentUserInput("dev", ORG, List.of()));

        assertThat(output.roleId()).isEqualTo("role-dev");
        assertThat(output.roleLabel()).isEqualTo("Desenvolvedor");
        assertThat(output.seniority()).isEqualTo("PLENO");
        assertThat(output.memberSince()).isEqualTo(joined);
    }

    @Test
    void should_fallBackToRoleName_whenRoleHasNoLabel() {
        User dev = new User("dev", ORG, "dev@x.com", null, "Dev", "role-custom", null, null, null, null, null,
                true, true, 1L, T, T);
        when(userRepository.findByIdAndOrganizationId("dev", ORG)).thenReturn(Optional.of(dev));
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(new Organization(ORG, "Org", "org", null,
                null, List.of(), List.of(), null, true, 1L, T, T)));
        when(roleRepository.findById("role-custom")).thenReturn(Optional.of(new Role("role-custom", ORG, "Squad Lead",
                null, false, null, new ArrayList<>(List.of("DASHBOARD_TEAM")), true, 1L, T, T)));
        EffectivePermissionService service = new EffectivePermissionService(userRepository, roleRepository,
                projectRepository, new CaffeinePermissionSnapshotCache(Duration.ofMinutes(5)));

        GetCurrentUserOutput output = new GetCurrentUserUseCase(userRepository, organizationRepository, service,
                roleRepository).execute(new GetCurrentUserInput("dev", ORG, List.of()));

        assertThat(output.roleLabel()).isEqualTo("Squad Lead");
        assertThat(output.seniority()).isNull();
    }
}

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

        GetCurrentUserOutput output = new GetCurrentUserUseCase(userRepository, organizationRepository, service)
                .execute(new GetCurrentUserInput("dev", ORG, List.of("USER_MANAGE")));

        assertThat(output.orgPermissions()).containsExactly("DASHBOARD_INDIVIDUAL", "REPOS_READ");
        assertThat(output.permissions()).isEqualTo(output.orgPermissions());
        assertThat(output.projectPermissions().get("A")).contains("REPOS_READ");
        assertThat(output.projectPermissions().get("B")).doesNotContain("REPOS_READ");
    }
}

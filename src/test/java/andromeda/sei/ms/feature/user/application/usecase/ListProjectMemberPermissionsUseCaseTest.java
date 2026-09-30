package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.application.dto.ProjectMemberPermissionsOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** project-scoped-permissions — project member panel: effective codes and project overrides. */
@ExtendWith(MockitoExtension.class)
class ListProjectMemberPermissionsUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private EffectivePermissionService effectivePermissionService;

    @InjectMocks private ListProjectMemberPermissionsUseCase useCase;

    @Test
    void should_listMembers_withEffectiveCodesAndOwnProjectOverridesOnly() {
        when(projectRepository.findActiveByIdAndOrganizationId("A", ORG)).thenReturn(Optional.of(
                new Project("A", ORG, "A", "a", null, true, List.of("dev", "ghost"), 1L, T, T)));
        User dev = new User("dev", ORG, "dev@x.com", null, "Dev", "r", null, null, null, null, null, true, true, 1L, T, T);
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(dev));
        List<PermissionOverride> overrides = List.of(
                new PermissionOverride("REPOS_READ", PermissionEffect.DENY, "A", "admin", T),
                new PermissionOverride("AZURE_READ", PermissionEffect.DENY, "B", "admin", T));
        when(effectivePermissionService.snapshot(ORG, "dev")).thenReturn(Optional.of(new UserPermissionSnapshot(
                ORG, "dev", "r", "TECH_LEAD", List.of("REPOS_READ", "DASHBOARD_TEAM"), overrides, Set.of("A"), 1L)));

        List<ProjectMemberPermissionsOutput> members = useCase.execute(ORG, "A");

        assertThat(members).hasSize(1);
        assertThat(members.getFirst().roleName()).isEqualTo("TECH_LEAD");
        assertThat(members.getFirst().effective()).containsExactly("DASHBOARD_TEAM");
        assertThat(members.getFirst().projectOverrides())
                .containsExactly(new ProjectMemberPermissionsOutput.ProjectOverride("REPOS_READ", "DENY"));
    }

    @Test
    void should_return404_when_projectIsOfAnotherOrganization() {
        when(projectRepository.findActiveByIdAndOrganizationId("X", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, "X")).isInstanceOf(ProjectNotFoundException.class);
    }
}

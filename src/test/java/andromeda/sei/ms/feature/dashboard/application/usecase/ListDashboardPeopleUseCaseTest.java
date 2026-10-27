package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** individual-outra-pessoa 1.4 — members of the project, active only, alphabetical, no e-mail. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListDashboardPeopleUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    @Test
    void should_listActiveMembersAlphabetically_withJobTitle_andNoEmail() {
        when(projectRepository.findActiveByIdAndOrganizationId("p1", ORG)).thenReturn(Optional.of(
                new Project("p1", ORG, "Orion", "orion", null, true, List.of("u-bia", "u-ana", "u-off", "u-no-role"),
                        0L, NOW, NOW)));
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                user("u-bia", "bia", "role-dev", true), user("u-ana", "Ana", "role-lead", true),
                user("u-off", "Off", "role-dev", false), user("u-no-role", "Caio", null, true),
                user("u-outsider", "Dani", "role-dev", true)));
        when(roleRepository.findById("role-dev")).thenReturn(Optional.of(role("DEVELOPER", "Desenvolvedor")));
        when(roleRepository.findById("role-lead")).thenReturn(Optional.of(role("TECH_LEAD", null)));

        var out = new ListDashboardPeopleUseCase(projectRepository, userRepository, roleRepository).execute(ORG, "p1");

        assertThat(out).extracting(ListDashboardPeopleUseCase.PersonOption::id)
                .containsExactly("u-ana", "u-bia", "u-no-role");
        assertThat(out).extracting(ListDashboardPeopleUseCase.PersonOption::jobTitle)
                .containsExactly("TECH_LEAD", "Desenvolvedor", null);
        assertThat(ListDashboardPeopleUseCase.PersonOption.class.getRecordComponents()).extracting("name")
                .containsExactly("id", "name", "jobTitle");
    }

    @Test
    void should_throwProjectNotFound_when_projectIsOfAnotherOrganization() {
        when(projectRepository.findActiveByIdAndOrganizationId("foreign", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ListDashboardPeopleUseCase(projectRepository, userRepository, roleRepository)
                .execute(ORG, "foreign")).isInstanceOf(ProjectNotFoundException.class);
    }

    private static User user(String id, String name, String roleId, boolean active) {
        return new User(id, ORG, id + "@example.com", "hash", name, roleId, null, null, null, null, null, true,
                active, 0L, NOW, NOW);
    }

    private static Role role(String name, String label) {
        Role r = new Role("r-" + name, ORG, name, label, true, null, List.of(), true, 0L, NOW, NOW);
        return r;
    }
}

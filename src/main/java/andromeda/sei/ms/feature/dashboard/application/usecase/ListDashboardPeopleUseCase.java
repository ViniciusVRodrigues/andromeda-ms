package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The active members of a project a team viewer can open the individual dashboard of: id, name
 * and job title (the label of the user's role), alphabetical, never the e-mail.
 */
@Service
@RequiredArgsConstructor
public class ListDashboardPeopleUseCase {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public List<PersonOption> execute(String orgId, String projectId) {
        Project project = projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Set<String> memberIds = Set.copyOf(project.getMemberIds());
        Map<String, String> titles = new HashMap<>();
        return userRepository.findAllByOrganizationId(orgId).stream()
                .filter(u -> u.getId() != null && u.isActive() && memberIds.contains(u.getId()))
                .map(u -> new PersonOption(u.getId(), displayName(u), titles.computeIfAbsent(
                        u.getRoleId() == null ? "" : u.getRoleId(), id -> jobTitle(id))))
                .sorted(Comparator.comparing(PersonOption::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private String jobTitle(String roleId) {
        if (roleId.isEmpty()) return null;
        return roleRepository.findById(roleId).map(ListDashboardPeopleUseCase::label).orElse(null);
    }

    private static String label(Role role) {
        return role.getLabel() != null && !role.getLabel().isBlank() ? role.getLabel() : role.getName();
    }

    private static String displayName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        return user.getId();
    }

    /** {@code jobTitle} may be null. */
    public record PersonOption(String id, String name, String jobTitle) {}
}

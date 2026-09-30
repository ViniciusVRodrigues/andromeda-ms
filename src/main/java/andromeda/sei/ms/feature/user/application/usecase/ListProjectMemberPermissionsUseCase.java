package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.application.dto.ProjectMemberPermissionsOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Members of a project with their effective project-scopable codes on it and their project-level
 * overrides (project-scoped-permissions, project member panel).
 */
@Component
@RequiredArgsConstructor
public class ListProjectMemberPermissionsUseCase {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EffectivePermissionService effectivePermissionService;

    public List<ProjectMemberPermissionsOutput> execute(String organizationId, String projectId) {
        Project project = projectRepository.findActiveByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Map<String, User> users = userRepository.findAllByOrganizationId(organizationId).stream()
                .filter(User::isActive)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        return project.getMemberIds().stream()
                .map(users::get)
                .filter(java.util.Objects::nonNull)
                .map(user -> toOutput(organizationId, projectId, user))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ProjectMemberPermissionsOutput> toOutput(String organizationId, String projectId, User user) {
        return effectivePermissionService.snapshot(organizationId, user.getId())
                .map(snapshot -> new ProjectMemberPermissionsOutput(
                        user.getId(), user.getName(), user.getEmail(), snapshot.roleName(),
                        List.copyOf(snapshot.resolver().codesOnProject(projectId)),
                        projectOverrides(snapshot, projectId)));
    }

    private static List<ProjectMemberPermissionsOutput.ProjectOverride> projectOverrides(
            UserPermissionSnapshot snapshot, String projectId) {
        return snapshot.overrides().stream()
                .filter(o -> projectId.equals(o.projectId()))
                .map(o -> new ProjectMemberPermissionsOutput.ProjectOverride(o.code(), o.effect().name()))
                .toList();
    }
}

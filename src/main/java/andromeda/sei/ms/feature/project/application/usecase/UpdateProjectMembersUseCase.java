package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectInvalidMemberException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.application.security.ProjectMembersChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UpdateProjectMembersUseCase {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Project execute(String projectId, List<String> memberIds) {
        String organizationId = TenantContextHolder.get();

        var project = projectRepository.findById(projectId)
                .filter(p -> p.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        for (String userId : memberIds) {
            userRepository.findByIdAndOrganizationId(userId, organizationId)
                    .orElseThrow(() -> new ProjectInvalidMemberException(userId));
        }

        project.setMemberIds(memberIds);
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        eventPublisher.publishEvent(new ProjectMembersChanged(organizationId, saved.getId()));
        return saved;
    }
}

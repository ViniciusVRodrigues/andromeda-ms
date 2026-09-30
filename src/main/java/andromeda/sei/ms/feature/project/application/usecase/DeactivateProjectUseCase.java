package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.application.security.ProjectMembersChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DeactivateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(String id) {
        String organizationId = TenantContextHolder.get();
        var project = projectRepository.findById(id)
                .filter(p -> p.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ProjectNotFoundException(id));

        project.setActive(false);
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        eventPublisher.publishEvent(new ProjectMembersChanged(organizationId, id));
    }
}

package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DeactivateProjectUseCase {

    private final ProjectRepository projectRepository;

    public void execute(String id) {
        String organizationId = TenantContextHolder.get();
        var project = projectRepository.findById(id)
                .filter(p -> p.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ProjectNotFoundException(id));

        project.setActive(false);
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
    }
}

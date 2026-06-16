package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UpdateProjectUseCase {

    private final ProjectRepository projectRepository;

    public Project execute(String id, String name, String description) {
        String organizationId = TenantContextHolder.get();
        var project = projectRepository.findById(id)
                .filter(p -> p.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ProjectNotFoundException(id));

        project.setName(name);
        project.setDescription(description);
        project.setUpdatedAt(Instant.now());
        return projectRepository.save(project);
    }
}

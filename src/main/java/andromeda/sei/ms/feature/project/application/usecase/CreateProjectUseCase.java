package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectSlugConflictException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;

    public Project execute(String name, String description) {
        String organizationId = TenantContextHolder.get();
        String slug = SlugUtils.toSlug(name);

        if (projectRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw new ProjectSlugConflictException(slug);
        }

        Instant now = Instant.now();
        var project = new Project(null, organizationId, name, slug, description, true, new java.util.ArrayList<>(), null, now, now);
        return projectRepository.save(project);
    }
}

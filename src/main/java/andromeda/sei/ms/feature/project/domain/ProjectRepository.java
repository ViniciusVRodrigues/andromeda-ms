package andromeda.sei.ms.feature.project.domain;

import java.util.Optional;

public interface ProjectRepository {

    Optional<Project> findById(String id);

    boolean existsByOrganizationIdAndSlug(String organizationId, String slug);

    Project save(Project project);
}

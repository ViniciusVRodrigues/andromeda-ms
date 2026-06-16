package andromeda.sei.ms.feature.project.domain;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    Optional<Project> findById(String id);

    List<Project> findAllActiveByOrganizationId(String organizationId);

    List<Project> findAllActiveByOrganizationIdAndMemberId(String organizationId, String memberId);

    boolean existsByOrganizationIdAndSlug(String organizationId, String slug);

    Project save(Project project);

    void removeUserFromAllProjects(String organizationId, String userId);
}

package andromeda.sei.ms.feature.project.infrastructure.persistence;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectMongoRepository mongoRepository;
    private final ProjectDocumentMapper mapper;

    @Override
    public Optional<Project> findById(String id) {
        return mongoRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndSlug(String organizationId, String slug) {
        return mongoRepository.existsByOrganizationIdAndSlug(organizationId, slug);
    }

    @Override
    public Project save(Project project) {
        ProjectDocument doc = mapper.toDocument(project);
        return mapper.toDomain(mongoRepository.save(doc));
    }
}

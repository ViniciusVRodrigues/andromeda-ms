package andromeda.sei.ms.feature.project.infrastructure.persistence;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public List<Project> findAllActiveByOrganizationId(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Project> findAllActiveByOrganizationIdAndMemberId(String organizationId, String memberId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrueAndMemberIdsContaining(organizationId, memberId)
                .stream().map(mapper::toDomain).toList();
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

    @Override
    public void removeUserFromAllProjects(String organizationId, String userId) {
        var docs = mongoRepository.findAllByOrganizationIdAndMemberIdsContaining(organizationId, userId);
        docs.forEach(doc -> {
            doc.getMemberIds().remove(userId);
            mongoRepository.save(doc);
        });
    }
}

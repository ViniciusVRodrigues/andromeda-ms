package andromeda.sei.ms.feature.project.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProjectMongoRepository extends MongoRepository<ProjectDocument, String> {

    boolean existsByOrganizationIdAndSlug(String organizationId, String slug);

    List<ProjectDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);

    List<ProjectDocument> findAllByOrganizationIdAndActiveTrueAndMemberIdsContaining(String organizationId, String memberId);

    List<ProjectDocument> findAllByOrganizationIdAndMemberIdsContaining(String organizationId, String memberId);
}

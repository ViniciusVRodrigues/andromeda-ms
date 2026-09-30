package andromeda.sei.ms.feature.project.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMongoRepository extends MongoRepository<ProjectDocument, String> {

    boolean existsByOrganizationIdAndSlug(String organizationId, String slug);

    Optional<ProjectDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<ProjectDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);

    List<ProjectDocument> findAllByOrganizationIdAndActiveTrueAndMemberIdsContaining(String organizationId, String memberId);

    List<ProjectDocument> findAllByOrganizationIdAndMemberIdsContaining(String organizationId, String memberId);
}

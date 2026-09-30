package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SprintMongoRepository extends MongoRepository<SprintDocument, String> {

    Optional<SprintDocument> findByOrganizationIdAndExternalIdAndTeamId(
            String organizationId, String externalId, String teamId);

    List<SprintDocument> findAllByOrganizationIdAndProjectIdAndPathAndActiveTrue(
            String organizationId, String projectId, String path);

    List<SprintDocument> findAllByOrganizationId(String organizationId);

    Optional<SprintDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<SprintDocument> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);
}

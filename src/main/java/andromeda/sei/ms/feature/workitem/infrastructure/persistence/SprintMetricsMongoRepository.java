package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SprintMetricsMongoRepository extends MongoRepository<SprintMetricsDocument, String> {

    Optional<SprintMetricsDocument> findByOrganizationIdAndSprintIdAndSource(
            String organizationId, String sprintId, WorkItem.Source source);

    List<SprintMetricsDocument> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);
}

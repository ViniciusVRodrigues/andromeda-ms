package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkItemActivityMongoRepository extends MongoRepository<WorkItemActivityDocument, String> {

    List<WorkItemActivityDocument> findAllByOrganizationIdAndWorkItemId(String organizationId, String workItemId);

    List<WorkItemActivityDocument> findAllByOrganizationIdAndActorUserIdAndDayAndActiveTrue(
            String organizationId, String actorUserId, LocalDate day);
}

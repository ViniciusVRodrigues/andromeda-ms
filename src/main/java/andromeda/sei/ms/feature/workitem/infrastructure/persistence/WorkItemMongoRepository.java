package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WorkItemMongoRepository extends MongoRepository<WorkItemDocument, String> {

    Optional<WorkItemDocument> findByOrganizationIdAndExternalIdAndSource(
            String organizationId, String externalId, WorkItem.Source source);

    List<WorkItemDocument> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    List<WorkItemDocument> findByOrganizationIdAndAssigneeUserIdAndSprintId(
            String organizationId, String assigneeUserId, String sprintId);

    List<WorkItemDocument> findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
            String organizationId, String sprintId, String assigneeUserId, WorkItem.ResolvedStatus resolvedStatus);

    List<WorkItemDocument> findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
            String organizationId, String assigneeUserId, String sprintId, String projectId);

    List<WorkItemDocument> findAllByOrganizationIdAndProjectIdAndSprintId(String organizationId, String projectId, String sprintId);
}

package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkItemMongoRepository extends MongoRepository<WorkItemDocument, String> {

    Optional<WorkItemDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    Optional<WorkItemDocument> findByOrganizationIdAndExternalIdAndSource(
            String organizationId, String externalId, WorkItem.Source source);

    List<WorkItemDocument> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    List<WorkItemDocument> findAllByOrganizationIdAndExternalIdAndActiveTrue(String organizationId, String externalId);

    List<WorkItemDocument> findByOrganizationIdAndAssigneeUserIdAndSprintId(
            String organizationId, String assigneeUserId, String sprintId);

    List<WorkItemDocument> findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
            String organizationId, String sprintId, String assigneeUserId, WorkItem.ResolvedStatus resolvedStatus);

    List<WorkItemDocument> findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
            String organizationId, String assigneeUserId, String sprintId, String projectId);

    List<WorkItemDocument> findAllByOrganizationIdAndProjectIdAndSprintId(String organizationId, String projectId, String sprintId);

    List<WorkItemDocument> findByOrganizationIdAndProjectIdAndActiveTrueOrderByExternalIdAsc(
            String organizationId, String projectId, Pageable pageable);

    long countByOrganizationIdAndProjectIdAndActiveTrue(String organizationId, String projectId);

    List<WorkItemDocument> findByOrganizationIdAndProjectIdAndParentExternalIdInAndActiveTrue(
            String organizationId, String projectId, List<String> parentExternalIds);

    List<WorkItemDocument> findByOrganizationIdAndProjectIdAndParentExternalIdAndActiveTrueOrderByExternalIdAsc(
            String organizationId, String projectId, String parentExternalId, Pageable pageable);


    List<WorkItemDocument> findAllByOrganizationIdAndExternalIdInAndSource(
            String organizationId, List<String> externalIds, WorkItem.Source source);

    List<WorkItemDocument> findAllByOrganizationIdAndProjectIdAndSprintIdInAndActiveTrue(
            String organizationId, String projectId, Collection<String> sprintIds);

    /** Half-open {@code [from, to)} — a derived {@code Between} would exclude {@code from}. */
    @Query("{ 'organizationId': ?0, 'projectId': ?1, 'active': true, 'completedAt': { '$gte': ?2, '$lt': ?3 } }")
    List<WorkItemDocument> findActiveCompletedInRange(String organizationId, String projectId, Instant from, Instant to);
}

package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkItemRepository {

    WorkItem save(WorkItem workItem);

    Optional<WorkItem> findById(String id);

    Optional<WorkItem> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    Optional<WorkItem> findByOrganizationIdAndExternalIdAndSource(
            String organizationId, String externalId, WorkItem.Source source);

    List<WorkItem> findAllByOrganizationIdAndProjectId(String organizationId, String projectId);

    void updateMetrics(String id, Double cycleTimeHours, Double leadTimeHours);

    void updateTokensPerSp(String id, Double tokensPerSp);

    List<WorkItem> findByOrganizationIdAndAssigneeUserIdAndSprintId(
            String organizationId, String userId, String sprintId);

    List<WorkItem> findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
            String organizationId, String sprintId, String assigneeUserId, WorkItem.ResolvedStatus status);

    void updateAssigneeUserIdByExternalIdOrEmail(String organizationId, String azureId, String email, String userId);

    List<WorkItem> findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
            String organizationId, String assigneeUserId, String sprintId, String projectId);

    List<WorkItem> findAllByOrganizationIdAndProjectIdAndSprintId(String organizationId, String projectId, String sprintId);

    /**
     * Returns a page of "root candidate" work items for a project — i.e. all active work items
     * in the project, ordered deterministically by externalId. Root/orphan/child classification
     * itself is applied in the application layer (see WorkItemHierarchyClassifier), since it
     * depends on the binding's hierarchyLevels (a different aggregate) rather than being
     * expressible as a Mongo query predicate alone.
     */
    WorkItemPageResult findRootCandidatesByOrganizationIdAndProjectId(
            String organizationId, String projectId, int page, int size);

    /**
     * Fetches every active work item in the project whose parentExternalId is one of the given
     * values. Used by the classifier to resolve parent types for rank validation across a whole
     * page of candidates in one query.
     */
 /** Batch lookup by external id, so a listing resolves all its references in one query. */
    List<WorkItem> findAllByOrganizationIdAndExternalIdInAndSource(
            String organizationId, List<String> externalIds, WorkItem.Source source);

    List<WorkItem> findByOrganizationIdAndProjectIdAndParentExternalIdIn(
            String organizationId, String projectId, List<String> parentExternalIds);

    /**
     * Fetches up to `limit` active children of a single parent, ordered by externalId ascending
     * for deterministic truncation.
     */
    List<WorkItem> findChildCandidatesByOrganizationIdAndParentExternalId(
            String organizationId, String projectId, String parentExternalId, int limit);

    /** Active items of several sprints of one project, in one query (team flow v2). Empty without ids. */
    List<WorkItem> findActiveByOrganizationIdAndProjectIdAndSprintIdIn(String organizationId, String projectId,
                                                                       Collection<String> sprintIds);

    /** Active items of one project completed in {@code [from, to)}, any sprint or backlog (team flow v2). */
    List<WorkItem> findActiveByOrganizationIdAndProjectIdAndCompletedAtInRange(String organizationId, String projectId,
                                                                               Instant from, Instant to);

    /** Ids of the project's active work items only — a lean read when the items themselves are not needed. */
    java.util.Set<String> findActiveIdsByOrganizationIdAndProjectId(String organizationId, String projectId);

    /**
     * Active work items of the project with only the fields period metrics read: id, external and
     * parent ids, sprint, resolved type and status, effort, complexity, completion, lead and cycle
     * time. Every other field is {@code null}. A lean read for organization-wide views over tens of
     * thousands of items; the default delegates, the Mongo adapter reads raw documents.
     */
    default List<WorkItem> findMetricFieldsByOrganizationIdAndProjectId(String organizationId, String projectId) {
        return findAllByOrganizationIdAndProjectId(organizationId, projectId).stream().filter(WorkItem::isActive).toList();
    }
}

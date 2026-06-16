package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;
import java.util.Optional;

public interface WorkItemRepository {

    WorkItem save(WorkItem workItem);

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
}

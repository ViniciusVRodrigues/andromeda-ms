package andromeda.sei.ms.feature.workitem.domain;

import java.util.Collection;
import java.util.List;

public interface WorkItemTransitionRepository {

    WorkItemTransition save(WorkItemTransition transition);

    List<WorkItemTransition> saveAll(List<WorkItemTransition> transitions);

    List<WorkItemTransition> findAllByWorkItemId(String workItemId);

    /**
     * Active transitions of several work items of one organization, in one query; empty without
     * ids. Backed by the {@code {organizationId, workItemId}} index.
     */
    List<WorkItemTransition> findActiveByOrganizationIdAndWorkItemIdIn(String organizationId,
                                                                       Collection<String> workItemIds);

    boolean existsByWorkItemId(String workItemId);

    WorkItemTransition upsertByCompositeKey(WorkItemTransition transition);
}

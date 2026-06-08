package andromeda.sei.ms.feature.workitem.domain;

import java.util.List;

public interface WorkItemTransitionRepository {

    WorkItemTransition save(WorkItemTransition transition);

    List<WorkItemTransition> saveAll(List<WorkItemTransition> transitions);

    List<WorkItemTransition> findAllByWorkItemId(String workItemId);

    boolean existsByWorkItemId(String workItemId);

    WorkItemTransition upsertByCompositeKey(WorkItemTransition transition);
}

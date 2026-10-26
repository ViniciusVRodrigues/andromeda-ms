package andromeda.sei.ms.feature.workitem.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

public interface WorkItemActivityRepository {

    /** Idempotent by {@code (organizationId, workItemId, revision)}: a repeat does not duplicate. */
    WorkItemActivity upsert(WorkItemActivity activity);

    /** Highest stored revision of the item, empty when it has no activity. */
    OptionalInt maxRevisionByWorkItemId(String organizationId, String workItemId);

    List<WorkItemActivity> findAllByWorkItemId(String organizationId, String workItemId);

    /** Active activity of one user on one UTC day. */
    List<WorkItemActivity> findByOrganizationIdAndActorUserIdAndDay(String organizationId, String actorUserId,
                                                                    LocalDate day);
}

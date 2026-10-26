package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItemActivity;
import org.springframework.stereotype.Component;

@Component
public class WorkItemActivityDocumentMapper {

    public WorkItemActivity toDomain(WorkItemActivityDocument doc) {
        return new WorkItemActivity(doc.getId(), doc.getOrganizationId(), doc.getWorkItemId(),
                doc.getExternalId(), doc.getRevision(), doc.getActorExternalId(), doc.getActorUniqueName(),
                doc.getActorUserId(), doc.getChangedAt(), doc.getDay(), doc.getChangedFields(),
                doc.isActive(), doc.getVersion(), doc.getCreatedAt(), doc.getUpdatedAt());
    }
}

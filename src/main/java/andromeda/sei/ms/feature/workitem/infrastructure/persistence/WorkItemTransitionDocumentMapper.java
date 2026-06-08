package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import org.springframework.stereotype.Component;

@Component
public class WorkItemTransitionDocumentMapper {

    public WorkItemTransition toDomain(WorkItemTransitionDocument doc) {
        return new WorkItemTransition(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getWorkItemId(),
                doc.getFromRawStatus(),
                doc.getToRawStatus(),
                doc.getToResolvedStatus(),
                doc.getOccurredAt(),
                doc.getRecordedAt(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public WorkItemTransitionDocument toDocument(WorkItemTransition transition) {
        return WorkItemTransitionDocument.builder()
                .id(transition.getId())
                .organizationId(transition.getOrganizationId())
                .workItemId(transition.getWorkItemId())
                .fromRawStatus(transition.getFromRawStatus())
                .toRawStatus(transition.getToRawStatus())
                .toResolvedStatus(transition.getToResolvedStatus())
                .occurredAt(transition.getOccurredAt())
                .recordedAt(transition.getRecordedAt())
                .active(transition.isActive())
                .version(transition.getVersion())
                .createdAt(transition.getCreatedAt())
                .updatedAt(transition.getUpdatedAt())
                .build();
    }
}

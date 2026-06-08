package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.springframework.stereotype.Component;

@Component
public class AiUsageAllocationDocumentMapper {

    public AiUsageAllocation toDomain(AiUsageAllocationDocument doc) {
        return new AiUsageAllocation(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getDay(),
                doc.getWorkItemId(),
                doc.getWorkItemTitle(),
                doc.getPercentage(),
                doc.getStatus(),
                doc.getAssignedBy(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public AiUsageAllocationDocument toDocument(AiUsageAllocation allocation) {
        return AiUsageAllocationDocument.builder()
                .id(allocation.getId())
                .organizationId(allocation.getOrganizationId())
                .userId(allocation.getUserId())
                .day(allocation.getDay())
                .workItemId(allocation.getWorkItemId())
                .workItemTitle(allocation.getWorkItemTitle())
                .percentage(allocation.getPercentage())
                .status(allocation.getStatus())
                .assignedBy(allocation.getAssignedBy())
                .active(allocation.isActive())
                .version(allocation.getVersion())
                .createdAt(allocation.getCreatedAt())
                .updatedAt(allocation.getUpdatedAt())
                .build();
    }
}

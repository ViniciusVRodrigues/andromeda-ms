package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import org.springframework.stereotype.Component;

@Component
public class SprintMetricsDocumentMapper {

    public SprintMetrics toDomain(SprintMetricsDocument doc) {
        return new SprintMetrics(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getProjectId(),
                doc.getSprintId(),
                doc.getSource(),
                doc.getVelocity(),
                doc.getAvgCycleTime(),
                doc.getAvgLeadTime(),
                doc.getCompletedItemCount(),
                doc.getTotalItemCount(),
                doc.getItemsWithPoints(),
                doc.getCalculatedAt(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public SprintMetricsDocument toDocument(SprintMetrics metrics) {
        return SprintMetricsDocument.builder()
                .id(metrics.getId())
                .organizationId(metrics.getOrganizationId())
                .projectId(metrics.getProjectId())
                .sprintId(metrics.getSprintId())
                .source(metrics.getSource())
                .velocity(metrics.getVelocity())
                .avgCycleTime(metrics.getAvgCycleTime())
                .avgLeadTime(metrics.getAvgLeadTime())
                .completedItemCount(metrics.getCompletedItemCount())
                .totalItemCount(metrics.getTotalItemCount())
                .itemsWithPoints(metrics.getItemsWithPoints())
                .calculatedAt(metrics.getCalculatedAt())
                .active(metrics.isActive())
                .version(metrics.getVersion())
                .createdAt(metrics.getCreatedAt())
                .updatedAt(metrics.getUpdatedAt())
                .build();
    }
}

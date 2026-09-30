package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import org.springframework.stereotype.Component;

@Component
public class SprintMetricsDocumentMapper {

    public SprintMetrics toDomain(SprintMetricsDocument doc) {
        SprintMetrics metrics = new SprintMetrics(
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
        metrics.setPlannedEffort(doc.getPlannedEffort());
        metrics.setCycleTimeP50(doc.getCycleTimeP50());
        metrics.setCycleTimeP90(doc.getCycleTimeP90());
        metrics.setLeadTimeP50(doc.getLeadTimeP50());
        metrics.setLeadTimeP90(doc.getLeadTimeP90());
        return metrics;
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
                .plannedEffort(metrics.getPlannedEffort())
                .cycleTimeP50(metrics.getCycleTimeP50())
                .cycleTimeP90(metrics.getCycleTimeP90())
                .leadTimeP50(metrics.getLeadTimeP50())
                .leadTimeP90(metrics.getLeadTimeP90())
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

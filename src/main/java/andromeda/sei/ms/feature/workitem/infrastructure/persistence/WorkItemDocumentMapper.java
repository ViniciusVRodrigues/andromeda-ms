package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.springframework.stereotype.Component;

@Component
public class WorkItemDocumentMapper {

    public WorkItem toDomain(WorkItemDocument doc) {
        WorkItem item = new WorkItem(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getProjectId(),
                doc.getSource(),
                doc.getExternalId(),
                doc.getExternalUpdatedAt(),
                doc.getTitle(),
                doc.getRawType(),
                doc.getResolvedType(),
                doc.getRawStatus(),
                doc.getResolvedStatus(),
                doc.getPriority(),
                doc.getStoryPoints(),
                doc.getLabels(),
                doc.getSprintId(),
                doc.getAssigneeExternalId(),
                doc.getAssigneeUserId(),
                doc.getAssigneeEmail(),
                doc.getParentExternalId(),
                doc.getCreatedAt(),
                doc.getStartedAt(),
                doc.getCompletedAt(),
                doc.getCycleTimeHours(),
                doc.getLeadTimeHours(),
                doc.getTokensPerSp(),
                doc.isActive(),
                doc.getVersion(),
                doc.getUpdatedAt(),
                doc.getComplexity(),
                doc.getComplexitySource(),
                doc.getComplexityOverriddenBy(),
                doc.getComplexityOverriddenAt(),
                doc.getEffort(),
                doc.getEffortSource(),
                doc.getEffortOverriddenBy(),
                doc.getEffortOverriddenAt()
        );
        /* Fora do construtor de propósito: ele já tem 38 parâmetros posicionais e é
         * chamado de vários pontos; um 39º só aumentaria a chance de troca silenciosa. */
        item.setSprintName(doc.getSprintName());
        item.setDeactivatedAt(doc.getDeactivatedAt());
        item.setDeactivationReason(doc.getDeactivationReason());
        return item;
    }

    public WorkItemDocument toDocument(WorkItem item) {
        return WorkItemDocument.builder()
                .id(item.getId())
                .organizationId(item.getOrganizationId())
                .projectId(item.getProjectId())
                .source(item.getSource())
                .externalId(item.getExternalId())
                .externalUpdatedAt(item.getExternalUpdatedAt())
                .title(item.getTitle())
                .rawType(item.getRawType())
                .resolvedType(item.getResolvedType())
                .rawStatus(item.getRawStatus())
                .resolvedStatus(item.getResolvedStatus())
                .priority(item.getPriority())
                .storyPoints(item.getStoryPoints())
                .labels(item.getLabels())
                .sprintId(item.getSprintId())
                .sprintName(item.getSprintName())
                .assigneeExternalId(item.getAssigneeExternalId())
                .assigneeUserId(item.getAssigneeUserId())
                .assigneeEmail(item.getAssigneeEmail())
                .parentExternalId(item.getParentExternalId())
                .createdAt(item.getCreatedAt())
                .startedAt(item.getStartedAt())
                .completedAt(item.getCompletedAt())
                .cycleTimeHours(item.getCycleTimeHours())
                .leadTimeHours(item.getLeadTimeHours())
                .tokensPerSp(item.getTokensPerSp())
                .active(item.isActive())
                .version(item.getVersion())
                .updatedAt(item.getUpdatedAt())
                .complexity(item.getComplexity())
                .complexitySource(item.getComplexitySource())
                .complexityOverriddenBy(item.getComplexityOverriddenBy())
                .complexityOverriddenAt(item.getComplexityOverriddenAt())
                .effort(item.getEffort())
                .effortSource(item.getEffortSource())
                .effortOverriddenBy(item.getEffortOverriddenBy())
                .effortOverriddenAt(item.getEffortOverriddenAt())
                .deactivatedAt(item.getDeactivatedAt())
                .deactivationReason(item.getDeactivationReason())
                .build();
    }
}

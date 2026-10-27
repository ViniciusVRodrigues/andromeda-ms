package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.application.dto.WorkItemDetailOutput;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetWorkItemDetailUseCase {
    private final WorkItemRepository workItemRepository;
    private final AzureProjectBindingRepository bindingRepository;

    public WorkItemDetailOutput execute(String organizationId, String id) {
        var item = workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                .orElseThrow(() -> new WorkItemNotFoundException(id));
        var config = bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(organizationId, item.getProjectId())
                .map(b -> b.getWorkflowConfig()).orElse(null);
        return new WorkItemDetailOutput(item.getId(), item.getExternalId(), item.getTitle(), item.getRawType(),
                item.getRawStatus(), item.getProjectId(), item.getSprintName(), item.getAssigneeEmail(),
                item.getPriority(), item.getLabels(), item.getParentExternalId(), item.getCreatedAt(),
                item.getStartedAt(), item.getCompletedAt(), item.getCycleTimeHours(), item.getLeadTimeHours(),
                item.getTokensPerPoint(), item.getTokensPerHour(),
                item.getResolvedType() != WorkItem.ResolvedType.EPIC
                        && item.getResolvedType() != WorkItem.ResolvedType.FEATURE,
                field(config, item, FieldMapping.InternalFieldTarget.COMPLEXITY, item.getComplexity(), item.getComplexitySource()),
                field(config, item, FieldMapping.InternalFieldTarget.EFFORT, item.getEffort(), item.getEffortSource()));
    }

    private WorkItemDetailOutput.FieldDetail field(WorkflowConfig config, WorkItem item,
            FieldMapping.InternalFieldTarget target, Double value, Object source) {
        var mapping = config == null ? null : config.resolveFieldMapping(target, item.getRawType()).orElse(null);
        return new WorkItemDetailOutput.FieldDetail(value, source == null ? null : source.toString(),
                mapping == null ? null : mapping.getAzureFieldReferenceName(),
                mapping == null ? null : mapping.getAzureFieldType());
    }
}

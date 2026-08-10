package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.dto.UpdateWorkItemManualFieldsInput;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemFieldNotEditableException;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UpdateWorkItemManualFieldsUseCase {

    private final WorkItemRepository workItemRepository;

    public WorkItem execute(String workItemId, String callingUserId, UpdateWorkItemManualFieldsInput input) {
        String orgId = TenantContextHolder.get();

        WorkItem workItem = workItemRepository.findById(workItemId)
                .filter(wi -> orgId.equals(wi.getOrganizationId()) && wi.isActive())
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        // Complexity/effort belong to the delivery tier (Story/Bug and Task). EPIC and FEATURE are
        // planning containers: their figures are the sum of what's below them, so accepting a
        // manual value there would create a number that silently contradicts its own children.
        if (!isEffortEditableType(workItem.getResolvedType())) {
            throw new WorkItemFieldNotEditableException(workItemId, workItem.getResolvedType());
        }

        Instant now = Instant.now();

        if (input.clearComplexityOverride()) {
            workItem.setComplexitySource(FieldValueSource.AZURE_SYNC);
            workItem.setComplexityOverriddenBy(null);
            workItem.setComplexityOverriddenAt(null);
        } else if (input.complexityPresent()) {
            workItem.setComplexity(input.complexity());
            workItem.setComplexitySource(FieldValueSource.MANUAL_OVERRIDE);
            workItem.setComplexityOverriddenBy(callingUserId);
            workItem.setComplexityOverriddenAt(now);
        }

        if (input.clearEffortOverride()) {
            workItem.setEffortSource(FieldValueSource.AZURE_SYNC);
            workItem.setEffortOverriddenBy(null);
            workItem.setEffortOverriddenAt(null);
        } else if (input.effortPresent()) {
            workItem.setEffort(input.effort());
            workItem.setEffortSource(FieldValueSource.MANUAL_OVERRIDE);
            workItem.setEffortOverriddenBy(callingUserId);
            workItem.setEffortOverriddenAt(now);
        }

        return workItemRepository.save(workItem);
    }

    /**
     * Delivery-tier types carry their own complexity/effort. A null resolvedType (a custom Azure
     * type the org never mapped) is allowed through rather than blocked, so an unmapped process
     * template doesn't lock users out of editing their own data.
     */
    private boolean isEffortEditableType(WorkItem.ResolvedType resolvedType) {
        return resolvedType == null
                || resolvedType == WorkItem.ResolvedType.STORY
                || resolvedType == WorkItem.ResolvedType.BUG
                || resolvedType == WorkItem.ResolvedType.TASK
                || resolvedType == WorkItem.ResolvedType.SUB_TASK;
    }
}

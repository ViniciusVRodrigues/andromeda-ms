package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.dto.UpdateWorkItemManualFieldsInput;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
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
}

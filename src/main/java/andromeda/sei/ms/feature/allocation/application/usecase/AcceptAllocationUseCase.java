package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AcceptAllocationUseCase {

    private final AiUsageAllocationRepository allocationRepository;
    private final WorkItemRepository workItemRepository;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;

    public void execute(String organizationId, String userId, String allocationId) {
        AiUsageAllocation allocation = allocationRepository.findById(allocationId, organizationId)
                .orElseThrow(() -> new AllocationNotFoundException(allocationId));

        if (!allocation.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("ALLOCATION_FORBIDDEN",
                    "You are not authorised to accept this allocation");
        }

        if (allocation.getStatus() != AiUsageAllocation.Status.PENDING_VALIDATION) {
            throw new AllocationAlreadyValidatedException(allocationId);
        }

        allocation.setStatus(AiUsageAllocation.Status.VALIDATED);
        allocation.setAssignedBy(AiUsageAllocation.AssignedBy.AUTO);
        allocation.setUpdatedAt(Instant.now());

        allocationRepository.save(allocation);
        allocationRepository.updateAllocationIdOnUsageRecords(allocation.getAiUsageIds(), allocationId);

        resolveProjectIds(organizationId, allocation)
                .forEach(projectId -> calculateAiRatiosUseCase.execute(organizationId, projectId));
    }

    /** Distinct projects of every split's work item (organization-scoped lookup). */
    private java.util.Set<String> resolveProjectIds(String organizationId, AiUsageAllocation allocation) {
        if (allocation.getSplits() == null) return java.util.Set.of();
        return allocation.getSplits().stream()
                .map(AllocationSplit::getWorkItemId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .map(id -> workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId))
                .flatMap(java.util.Optional::stream)
                .map(andromeda.sei.ms.feature.workitem.domain.WorkItem::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}

package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AcceptAllocationUseCase {

    private final AiUsageAllocationRepository allocationRepository;

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
    }
}

package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualAllocateUseCase {

    private static final BigDecimal ONE_SCALED = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;

    public void execute(String organizationId, String userId, String allocationId,
                        LocalDate day, List<AllocationSplit> splits) {
        validateSplits(splits);

        if (allocationId != null) {
            updateExistingAllocation(organizationId, userId, allocationId, splits);
        } else {
            createNewAllocation(organizationId, userId, day, splits);
        }
    }

    private void validateSplits(List<AllocationSplit> splits) {
        BigDecimal sum = splits.stream()
                .map(AllocationSplit::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        if (sum.compareTo(ONE_SCALED) != 0) {
            throw new AllocationSplitsInvalidException();
        }
    }

    private void updateExistingAllocation(String organizationId, String userId,
                                          String allocationId, List<AllocationSplit> splits) {
        AiUsageAllocation allocation = allocationRepository.findById(allocationId, organizationId)
                .orElseThrow(() -> new AllocationNotFoundException(allocationId));

        if (!allocation.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("ALLOCATION_FORBIDDEN",
                    "You are not authorised to modify this allocation");
        }

        if (allocation.getStatus() == AiUsageAllocation.Status.VALIDATED) {
            throw new AllocationAlreadyValidatedException(allocationId);
        }

        allocation.setSplits(splits);
        allocation.setStatus(AiUsageAllocation.Status.VALIDATED);
        allocation.setAssignedBy(AiUsageAllocation.AssignedBy.MANUAL);
        allocation.setUpdatedAt(Instant.now());

        allocationRepository.save(allocation);
        allocationRepository.updateAllocationIdOnUsageRecords(allocation.getAiUsageIds(), allocationId);
    }

    private void createNewAllocation(String organizationId, String userId,
                                     LocalDate day, List<AllocationSplit> splits) {
        List<String> usageIds = aiUsageRepository
                .findUnallocatedByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .stream().map(AIUsage::getId).toList();

        Instant now = Instant.now();
        AiUsageAllocation allocation = new AiUsageAllocation(
                null, organizationId, userId, day,
                usageIds, splits,
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, null, now, now);

        AiUsageAllocation saved = allocationRepository.save(allocation);
        allocationRepository.updateAllocationIdOnUsageRecords(usageIds, saved.getId());
    }
}

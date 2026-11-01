package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalService;
import andromeda.sei.ms.feature.allocation.application.AllocationSplitsValidator;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the editor calls before saving (uso-ia-fora-de-entrega, design 2.2): the same policy a manual
 * allocation of the day would hit, with nothing written. A day without anything to allocate needs no
 * approval; the save itself answers that with its own error.
 */
@Service
@RequiredArgsConstructor
public class PreviewAllocationApprovalUseCase {

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final AllocationApprovalService approvalService;

    public AllocationApprovalPolicy.Decision execute(String organizationId, String userId, LocalDate day,
                                                     String allocationId, List<AllocationSplit> rawSplits) {
        List<AllocationSplit> splits = AllocationSplitsValidator.normalize(rawSplits);
        AllocationSplitsValidator.validate(splits);

        LocalDate effectiveDay = day;
        if (allocationId != null) {
            AiUsageAllocation allocation = allocationRepository.findById(allocationId, organizationId)
                    .orElseThrow(() -> new AllocationNotFoundException(allocationId));
            if (!allocation.getUserId().equals(userId)) {
                throw new ForbiddenOperationException("ALLOCATION_FORBIDDEN",
                        "You are not authorised to modify this allocation");
            }
            effectiveDay = allocation.getDay();
        }

        ManualAllocateUseCase.DayRows rows = ManualAllocateUseCase.collectDayRows(
                allocationRepository, aiUsageRepository, organizationId, userId, effectiveDay);
        if (rows.usageIds().isEmpty()) return AllocationApprovalPolicy.Decision.free();
        return approvalService.evaluate(organizationId, userId, effectiveDay, new ArrayList<>(rows.usageIds()),
                splits, rows.replaced().stream().map(AiUsageAllocation::getId).toList());
    }
}

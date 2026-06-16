package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationHistoryOutput;
import andromeda.sei.ms.feature.allocation.application.dto.SplitOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class GetAllocationHistoryUseCase {

    private final AiUsageAllocationRepository allocationRepository;

    public Page<AllocationHistoryOutput> execute(String organizationId, String userId,
                                                  YearMonth month, Pageable pageable) {
        return allocationRepository
                .findValidatedByOrgUserAndOptionalMonth(organizationId, userId, month, pageable)
                .map(allocation -> new AllocationHistoryOutput(
                        allocation.getId(),
                        allocation.getDay(),
                        allocation.getSplits().stream()
                                .map(s -> new SplitOutput(
                                        s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage()))
                                .toList(),
                        allocation.getAssignedBy(),
                        allocation.getUpdatedAt()));
    }
}

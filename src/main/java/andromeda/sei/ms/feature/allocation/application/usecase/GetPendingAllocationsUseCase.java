package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryOutput;
import andromeda.sei.ms.feature.allocation.application.dto.PendingAllocationDayOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPendingAllocationsUseCase {

    private final AiUsageAllocationRepository allocationRepository;

    public List<PendingAllocationDayOutput> execute(String organizationId, String userId) {
        List<AiUsageAllocation> pending = allocationRepository
                .findPendingByOrganizationIdAndUserId(organizationId, userId);

        Map<java.time.LocalDate, List<AiUsageAllocation>> byDay = pending.stream()
                .collect(Collectors.groupingBy(AiUsageAllocation::getDay));

        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    AiUsageAllocation.Status status = e.getValue().stream()
                            .map(AiUsageAllocation::getStatus)
                            .findFirst()
                            .orElse(AiUsageAllocation.Status.UNALLOCATED);

                    List<AllocationEntryOutput> entries = e.getValue().stream()
                            .map(a -> new AllocationEntryOutput(
                                    a.getWorkItemId(), a.getWorkItemTitle(),
                                    a.getPercentage(), a.getAssignedBy()))
                            .toList();

                    return new PendingAllocationDayOutput(e.getKey(), status, entries);
                })
                .toList();
    }
}

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
                    AiUsageAllocation first = e.getValue().get(0);
                    AiUsageAllocation.Status status = first.getStatus();

                    List<AllocationEntryOutput> entries = first.getSplits() == null ? List.of() :
                            first.getSplits().stream()
                                    .map(s -> new AllocationEntryOutput(
                                            s.getWorkItemId(), s.getWorkItemTitle(),
                                            s.getPercentage(), first.getAssignedBy()))
                                    .toList();

                    return new PendingAllocationDayOutput(e.getKey(), status, entries);
                })
                .toList();
    }
}

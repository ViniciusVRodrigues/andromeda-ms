package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.time.LocalDate;
import java.util.List;

public record PendingAllocationDayOutput(
        LocalDate day,
        AiUsageAllocation.Status status,
        List<AllocationEntryOutput> allocations
) {}

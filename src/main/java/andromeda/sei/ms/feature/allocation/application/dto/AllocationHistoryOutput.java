package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AllocationHistoryOutput(
        String id,
        LocalDate day,
        List<SplitOutput> splits,
        AiUsageAllocation.AssignedBy assignedBy,
        Instant updatedAt
) {}

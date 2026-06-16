package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AllocationHistoryResponse(
        String id,
        LocalDate day,
        List<SplitResponse> splits,
        AiUsageAllocation.AssignedBy assignedBy,
        Instant updatedAt
) {}

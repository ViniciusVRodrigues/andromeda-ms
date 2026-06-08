package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.math.BigDecimal;

public record AllocationEntryResponse(
        String workItemId,
        String workItemTitle,
        BigDecimal percentage,
        AiUsageAllocation.AssignedBy assignedBy
) {}

package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.math.BigDecimal;

public record AllocationEntryOutput(
        String workItemId,
        String workItemTitle,
        BigDecimal percentage,
        AiUsageAllocation.AssignedBy assignedBy
) {}

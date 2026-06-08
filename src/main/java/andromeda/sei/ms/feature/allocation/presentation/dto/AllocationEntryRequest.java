package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.math.BigDecimal;

public record AllocationEntryRequest(
        String workItemId,
        String workItemTitle,
        BigDecimal percentage
) {}

package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;

import java.math.BigDecimal;

public record SplitResponse(AllocationSplit.Kind kind, String workItemId, String workItemTitle, BigDecimal percentage,
                            AllocationSplit.Category category, String note) {
    public SplitResponse(String workItemId, String workItemTitle, BigDecimal percentage) {
        this(AllocationSplit.Kind.ITEM, workItemId, workItemTitle, percentage, null, null);
    }
}

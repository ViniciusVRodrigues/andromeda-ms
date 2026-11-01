package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;

import java.math.BigDecimal;

public record SplitOutput(AllocationSplit.Kind kind, String workItemId, String workItemTitle, BigDecimal percentage,
                          AllocationSplit.Category category, String note) {
    public SplitOutput(String workItemId, String workItemTitle, BigDecimal percentage) {
        this(AllocationSplit.Kind.ITEM, workItemId, workItemTitle, percentage, null, null);
    }
}

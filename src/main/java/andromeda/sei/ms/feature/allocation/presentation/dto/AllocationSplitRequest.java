package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * {@code kind} absent is an ITEM. What each kind requires is validated by the use case
 * (422 {@code ALLOCATION_SPLITS_INVALID}), not here.
 */
public record AllocationSplitRequest(
        AllocationSplit.Kind kind,
        String workItemId,
        String workItemTitle,
        @NotNull @PositiveOrZero BigDecimal percentage,
        AllocationSplit.Category category,
        String note
) {
    public AllocationSplitRequest(String workItemId, String workItemTitle, BigDecimal percentage) {
        this(null, workItemId, workItemTitle, percentage, null, null);
    }
}

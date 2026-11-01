package andromeda.sei.ms.feature.allocation.application.dto;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;

import java.math.BigDecimal;

/**
 * One entry of a day suggestion; overriddenMark is the task the person marked and the branch overruled.
 * kind OUT_OF_DELIVERY has no work item and carries category and note.
 */
public record SuggestionItemOutput(String workItemId, String workItemTitle, BigDecimal percentage,
                                   String displayId, String source, OverriddenMark overriddenMark,
                                   AllocationSplit.Kind kind, AllocationSplit.Category category, String note) {

    public SuggestionItemOutput(String workItemId, String workItemTitle, BigDecimal percentage,
                                String displayId, String source, OverriddenMark overriddenMark) {
        this(workItemId, workItemTitle, percentage, displayId, source, overriddenMark,
                AllocationSplit.Kind.ITEM, null, null);
    }

    public record OverriddenMark(String displayId) {}
}

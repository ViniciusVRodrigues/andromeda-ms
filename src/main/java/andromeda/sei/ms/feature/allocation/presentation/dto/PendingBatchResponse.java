package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PendingBatchResponse(
        LocalDate day,
        long totalInputTokens,
        long totalOutputTokens,
        long totalCacheReadTokens,
        long totalCacheWriteTokens,
        int totalInteractions,
        BigDecimal estimatedCostUsd,
        String suggestionAllocationId,
        List<String> allocationIds,
        List<SuggestionItem> suggestion,
        Review review
) {
    public record SuggestionItem(String workItemId, String workItemTitle, BigDecimal percentage,
                                 String displayId, String source, OverriddenMark overriddenMark,
                                 AllocationSplit.Kind kind, AllocationSplit.Category category, String note) {
        public SuggestionItem(String workItemId, String workItemTitle, BigDecimal percentage,
                              String displayId, String source, OverriddenMark overriddenMark) {
            this(workItemId, workItemTitle, percentage, displayId, source, overriddenMark,
                    AllocationSplit.Kind.ITEM, null, null);
        }
    }

    public record OverriddenMark(String displayId) {}

    public record Review(String state, List<String> reasons, List<Detail> details) {}

    public record Detail(String code, String workItemId, String displayId, Long ratio) {}
}

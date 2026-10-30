package andromeda.sei.ms.feature.allocation.presentation.dto;

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
                                 String displayId, String source, OverriddenMark overriddenMark) {}

    public record OverriddenMark(String displayId) {}

    public record Review(String state, List<String> reasons, List<Detail> details) {}

    public record Detail(String code, String workItemId, String displayId, Long ratio) {}
}

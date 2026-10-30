package andromeda.sei.ms.feature.allocation.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PendingBatchOutput(
        LocalDate day,
        long totalInputTokens,
        long totalOutputTokens,
        long totalCacheReadTokens,
        long totalCacheWriteTokens,
        int totalInteractions,
        BigDecimal estimatedCostUsd,
        String suggestionAllocationId,
        List<String> allocationIds,
        List<SuggestionItemOutput> suggestion,
        ReviewOutput review
) {}

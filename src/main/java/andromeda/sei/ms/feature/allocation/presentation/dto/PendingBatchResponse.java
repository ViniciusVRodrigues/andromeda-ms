package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PendingBatchResponse(
        LocalDate day,
        long totalInputTokens,
        long totalOutputTokens,
        int totalInteractions,
        BigDecimal estimatedCostUsd,
        String suggestionAllocationId,
        List<SplitResponse> suggestion
) {}

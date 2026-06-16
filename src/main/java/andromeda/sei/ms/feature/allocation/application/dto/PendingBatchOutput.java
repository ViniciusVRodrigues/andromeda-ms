package andromeda.sei.ms.feature.allocation.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PendingBatchOutput(
        LocalDate day,
        long totalInputTokens,
        long totalOutputTokens,
        int totalInteractions,
        BigDecimal estimatedCostUsd,
        String suggestionAllocationId,
        List<SplitOutput> suggestion
) {}

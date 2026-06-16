package andromeda.sei.ms.feature.aiusage.presentation.dto;

import java.math.BigDecimal;

public record AiUsageSummaryResponse(
        long totalInputTokens,
        long totalOutputTokens,
        long totalInteractions,
        BigDecimal totalCostUsd
) {}

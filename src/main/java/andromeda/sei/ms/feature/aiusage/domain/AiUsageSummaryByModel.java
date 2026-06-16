package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;

public record AiUsageSummaryByModel(
        String model,
        long totalInputTokens,
        long totalOutputTokens,
        long totalInteractions,
        BigDecimal totalCostUsd
) {}

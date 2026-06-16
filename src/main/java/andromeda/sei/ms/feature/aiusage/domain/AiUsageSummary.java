package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;

public record AiUsageSummary(
        long totalInputTokens,
        long totalOutputTokens,
        long totalInteractions,
        BigDecimal totalCostUsd
) {}

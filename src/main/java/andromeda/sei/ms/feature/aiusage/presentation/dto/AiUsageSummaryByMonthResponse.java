package andromeda.sei.ms.feature.aiusage.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiUsageSummaryByMonthResponse(
        String month,
        long totalInputTokens,
        long totalOutputTokens,
        int totalInteractions,
        BigDecimal totalCostUsd,
        List<ModelBreakdownResponse> modelBreakdown
) {}

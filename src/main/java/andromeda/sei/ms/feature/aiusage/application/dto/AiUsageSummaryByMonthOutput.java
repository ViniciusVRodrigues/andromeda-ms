package andromeda.sei.ms.feature.aiusage.application.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record AiUsageSummaryByMonthOutput(
        YearMonth month,
        long totalInputTokens,
        long totalOutputTokens,
        int totalInteractions,
        BigDecimal totalCostUsd,
        List<ModelBreakdownOutput> modelBreakdown
) {}

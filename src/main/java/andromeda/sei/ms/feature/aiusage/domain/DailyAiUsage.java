package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAiUsage(
        LocalDate day,
        long inputTokens,
        long outputTokens,
        long interactions,
        BigDecimal costUsd
) {}

package andromeda.sei.ms.feature.aiusage.domain;

import java.math.BigDecimal;

/** AI usage of one person over a period, summed in the database: cost and tokens. */
public record AiUsageUserTotals(String userId, BigDecimal costUsd, long inputTokens, long outputTokens) {}

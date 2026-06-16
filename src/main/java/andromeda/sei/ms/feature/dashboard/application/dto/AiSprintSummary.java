package andromeda.sei.ms.feature.dashboard.application.dto;

import java.math.BigDecimal;

public record AiSprintSummary(long totalTokens, BigDecimal totalCostUsd, int unallocatedDaysCount) {}

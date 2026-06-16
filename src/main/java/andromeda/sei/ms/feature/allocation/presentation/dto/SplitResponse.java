package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.math.BigDecimal;

public record SplitResponse(String workItemId, String workItemTitle, BigDecimal percentage) {}

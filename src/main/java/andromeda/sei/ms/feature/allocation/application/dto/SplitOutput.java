package andromeda.sei.ms.feature.allocation.application.dto;

import java.math.BigDecimal;

public record SplitOutput(String workItemId, String workItemTitle, BigDecimal percentage) {}

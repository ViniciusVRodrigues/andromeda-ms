package andromeda.sei.ms.feature.organization.presentation.dto;

import java.math.BigDecimal;

public record AiPriceEntryResponse(String model, BigDecimal pricePerKInput, BigDecimal pricePerKOutput) {}

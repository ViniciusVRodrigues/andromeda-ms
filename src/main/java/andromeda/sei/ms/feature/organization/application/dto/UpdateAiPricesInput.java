package andromeda.sei.ms.feature.organization.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdateAiPricesInput(List<AiPriceEntryInput> entries) {

    public record AiPriceEntryInput(String model, BigDecimal pricePerKInput, BigDecimal pricePerKOutput) {}
}

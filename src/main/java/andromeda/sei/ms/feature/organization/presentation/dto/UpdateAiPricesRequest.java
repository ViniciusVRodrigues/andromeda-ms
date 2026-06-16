package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdateAiPricesRequest(List<AiPriceEntryRequest> entries) {

    public record AiPriceEntryRequest(
            @NotBlank String model,
            @NotNull BigDecimal pricePerKInput,
            @NotNull BigDecimal pricePerKOutput
    ) {}
}

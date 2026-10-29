package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdateAiPricesRequest(@Valid List<AiPriceEntryRequest> entries) {

    public record AiPriceEntryRequest(
            @NotBlank String model,
            @DecimalMin("0") BigDecimal pricePerKInput,
            @NotNull @DecimalMin("0") BigDecimal pricePerKOutput,
            @DecimalMin("0") BigDecimal pricePerKCacheRead,
            @DecimalMin("0") BigDecimal pricePerKCacheWrite
    ) {}
}

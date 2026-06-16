package andromeda.sei.ms.feature.allocation.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AllocationSplitRequest(
        @NotBlank String workItemId,
        String workItemTitle,
        @NotNull @PositiveOrZero BigDecimal percentage
) {}

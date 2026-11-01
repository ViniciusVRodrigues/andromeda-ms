package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AllocationPolicyRequest(@NotNull @Min(0) @Max(100) Integer outOfDeliveryCapPercent) {}

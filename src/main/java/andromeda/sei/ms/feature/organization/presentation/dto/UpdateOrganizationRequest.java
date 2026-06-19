package andromeda.sei.ms.feature.organization.presentation.dto;

import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

public record UpdateOrganizationRequest(
        @NotEmpty List<String> seniorities,
        BigDecimal subscriptionCostPerUserUsd
) {
}

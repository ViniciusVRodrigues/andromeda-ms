package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.application.dto.AllocationPolicyOutput;

public record AllocationPolicyResponse(int outOfDeliveryCapPercent) {

    public static AllocationPolicyResponse from(AllocationPolicyOutput output) {
        return new AllocationPolicyResponse(output.outOfDeliveryCapPercent());
    }
}

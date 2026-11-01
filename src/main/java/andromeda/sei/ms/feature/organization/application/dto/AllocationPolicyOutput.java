package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.organization.domain.AllocationPolicy;

public record AllocationPolicyOutput(int outOfDeliveryCapPercent) {

    public static AllocationPolicyOutput from(AllocationPolicy policy) {
        return new AllocationPolicyOutput(policy.outOfDeliveryCapPercent());
    }
}

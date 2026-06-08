package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AllocationInvalidSumException extends BusinessRuleException {
    public AllocationInvalidSumException() {
        super("ALLOCATION_INVALID_SUM", "Allocation percentages must sum to 1.0");
    }
}

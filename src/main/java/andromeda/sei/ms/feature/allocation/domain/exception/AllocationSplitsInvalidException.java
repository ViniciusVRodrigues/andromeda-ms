package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AllocationSplitsInvalidException extends BusinessRuleException {

    public AllocationSplitsInvalidException() {
        super("ALLOCATION_SPLITS_INVALID",
                "Allocation splits percentages must sum to 1.0");
    }

    public AllocationSplitsInvalidException(String detail) {
        super("ALLOCATION_SPLITS_INVALID", detail);
    }
}

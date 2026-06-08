package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AllocationEmptyException extends BusinessRuleException {
    public AllocationEmptyException() {
        super("ALLOCATION_EMPTY", "At least one work item must be allocated");
    }
}

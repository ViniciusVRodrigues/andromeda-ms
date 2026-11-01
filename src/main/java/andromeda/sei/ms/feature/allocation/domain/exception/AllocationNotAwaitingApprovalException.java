package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** The allocation is not AWAITING_APPROVAL (anymore); {@code decidedByName} is who decided it, when known. */
public class AllocationNotAwaitingApprovalException extends ResourceConflictException {

    private final String decidedByName;

    public AllocationNotAwaitingApprovalException(String allocationId, String decidedByName) {
        super("ALLOCATION_NOT_AWAITING_APPROVAL", "Allocation is not awaiting approval: " + allocationId);
        this.decidedByName = decidedByName;
    }

    public String getDecidedByName() {
        return decidedByName;
    }
}

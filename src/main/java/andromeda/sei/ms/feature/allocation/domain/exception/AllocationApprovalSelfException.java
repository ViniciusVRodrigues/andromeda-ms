package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/** Nobody approves or returns their own AI usage. */
public class AllocationApprovalSelfException extends ForbiddenOperationException {

    public AllocationApprovalSelfException(String allocationId) {
        super("ALLOCATION_APPROVAL_SELF", "You cannot decide the approval of your own allocation " + allocationId);
    }
}

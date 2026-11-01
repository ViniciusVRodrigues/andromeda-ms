package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/** The caller does not hold AI_ALLOCATION_APPROVE on a project that approves this allocation. */
public class AllocationApprovalForbiddenException extends ForbiddenOperationException {

    public AllocationApprovalForbiddenException(String allocationId) {
        super("ALLOCATION_APPROVAL_FORBIDDEN", "You cannot decide the approval of allocation " + allocationId);
    }
}

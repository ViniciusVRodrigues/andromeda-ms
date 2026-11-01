package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** Someone else changed the allocation between the read and the write (optimistic locking). */
public class AllocationVersionConflictException extends ResourceConflictException {

    public AllocationVersionConflictException(String allocationId) {
        super("ALLOCATION_CONFLICT", "Allocation was changed by someone else, reload and try again: " + allocationId);
    }
}

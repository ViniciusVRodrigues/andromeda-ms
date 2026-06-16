package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

public class AllocationAlreadyValidatedException extends ResourceConflictException {

    public AllocationAlreadyValidatedException(String allocationId) {
        super("ALLOCATION_ALREADY_VALIDATED", "Allocation has already been validated: " + allocationId);
    }
}

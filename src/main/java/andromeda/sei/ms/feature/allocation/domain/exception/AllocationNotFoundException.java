package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class AllocationNotFoundException extends ResourceNotFoundException {

    public AllocationNotFoundException(String allocationId) {
        super("ALLOCATION_NOT_FOUND", "Allocation not found: " + allocationId);
    }
}

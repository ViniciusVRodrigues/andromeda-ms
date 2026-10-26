package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

import java.time.LocalDate;

public class AllocationNothingToAllocateException extends BusinessRuleException {

    public AllocationNothingToAllocateException(LocalDate day) {
        super("ALLOCATION_NOTHING_TO_ALLOCATE", "No unallocated AI usage to allocate on " + day);
    }
}

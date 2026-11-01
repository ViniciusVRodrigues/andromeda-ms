package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AllocationReturnNoteInvalidException extends BusinessRuleException {

    public AllocationReturnNoteInvalidException() {
        super("ALLOCATION_RETURN_NOTE_INVALID", "The note of a returned allocation must have 1 to 280 characters");
    }
}

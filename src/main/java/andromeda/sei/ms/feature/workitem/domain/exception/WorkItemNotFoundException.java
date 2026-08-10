package andromeda.sei.ms.feature.workitem.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class WorkItemNotFoundException extends ResourceNotFoundException {

    public WorkItemNotFoundException(String id) {
        super("WORK_ITEM_NOT_FOUND", "Work item not found: " + id);
    }
}

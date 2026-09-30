package andromeda.sei.ms.feature.workitem.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class SprintNotFoundException extends ResourceNotFoundException {

    public SprintNotFoundException(String sprintId) {
        super("SPRINT_NOT_FOUND", "Sprint not found: " + sprintId);
    }
}

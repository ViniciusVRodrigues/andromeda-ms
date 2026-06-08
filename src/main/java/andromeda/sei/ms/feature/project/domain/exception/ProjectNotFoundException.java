package andromeda.sei.ms.feature.project.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class ProjectNotFoundException extends ResourceNotFoundException {

    public ProjectNotFoundException(String id) {
        super("PROJECT_NOT_FOUND", "Project not found: " + id);
    }
}

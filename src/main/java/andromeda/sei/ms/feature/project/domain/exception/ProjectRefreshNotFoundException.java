package andromeda.sei.ms.feature.project.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class ProjectRefreshNotFoundException extends ResourceNotFoundException {

    public ProjectRefreshNotFoundException(String syncId) {
        super("PROJECT_REFRESH_NOT_FOUND", "Project refresh not found: " + syncId);
    }
}

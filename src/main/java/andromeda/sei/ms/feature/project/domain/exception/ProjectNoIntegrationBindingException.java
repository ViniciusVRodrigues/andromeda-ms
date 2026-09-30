package andromeda.sei.ms.feature.project.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

public class ProjectNoIntegrationBindingException extends ResourceConflictException {

    public ProjectNoIntegrationBindingException(String projectId) {
        super("PROJECT_NO_INTEGRATION_BINDING",
                "Project has no active, sync-enabled integration binding to refresh: " + projectId);
    }
}

package andromeda.sei.ms.feature.project.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

/** The user is not an active member of the project (or does not exist in the organization). */
public class ProjectMemberNotFoundException extends ResourceNotFoundException {

    public ProjectMemberNotFoundException(String projectId, String userId) {
        super("PROJECT_MEMBER_NOT_FOUND", "Member " + userId + " not found in project " + projectId);
    }
}

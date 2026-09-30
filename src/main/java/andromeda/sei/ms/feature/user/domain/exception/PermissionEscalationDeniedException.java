package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * The caller tried to add or remove an override for a code they do not hold at that scope
 * (project-scoped-permissions D7).
 */
public class PermissionEscalationDeniedException extends ForbiddenOperationException {

    public PermissionEscalationDeniedException(String code, String projectId) {
        super("PERMISSION_ESCALATION_DENIED", "Cannot grant or revoke " + code
                + (projectId == null ? " at organization level" : " on project " + projectId)
                + " without holding it at that scope");
    }
}

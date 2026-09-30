package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** A project-level override was requested for an ORG_ONLY code. */
public class PermissionNotProjectScopableException extends BusinessRuleException {

    public PermissionNotProjectScopableException(String code) {
        super("PERMISSION_NOT_PROJECT_SCOPABLE", "Permission cannot be scoped to a project: " + code);
    }
}

package andromeda.sei.ms.feature.role.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class RoleInvalidNameException extends BusinessRuleException {

    public RoleInvalidNameException() {
        super("ROLE_INVALID_NAME", "Role name is required and must have at most 60 characters");
    }
}

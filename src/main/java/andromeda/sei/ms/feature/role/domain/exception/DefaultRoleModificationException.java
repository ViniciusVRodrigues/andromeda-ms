package andromeda.sei.ms.feature.role.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class DefaultRoleModificationException extends BusinessRuleException {

    public DefaultRoleModificationException() {
        super("ROLE_DEFAULT_IMMUTABLE", "Cannot modify or delete a default role");
    }
}

package andromeda.sei.ms.feature.role.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class RoleInUseException extends BusinessRuleException {

    public RoleInUseException(String roleId) {
        super("ROLE_IN_USE", "Cannot deactivate role '" + roleId + "': it is assigned to active users");
    }
}

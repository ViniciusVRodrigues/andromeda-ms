package andromeda.sei.ms.feature.role.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class RolePermissionNotAssignableException extends BusinessRuleException {

    public RolePermissionNotAssignableException(String code) {
        super("ROLE_PERMISSION_NOT_ASSIGNABLE", "Permission cannot be assigned to a role: " + code);
    }
}

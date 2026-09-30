package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** An override names a code missing from the permission catalog, or has no effect. */
public class PermissionCodeInvalidException extends BusinessRuleException {

    public PermissionCodeInvalidException(String code) {
        super("PERMISSION_CODE_INVALID", "Invalid permission override: " + code);
    }
}

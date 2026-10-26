package andromeda.sei.ms.feature.role.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

public class RoleDuplicateNameException extends ResourceConflictException {

    public RoleDuplicateNameException(String name) {
        super("ROLE_DUPLICATE_NAME", "A role named '" + name + "' already exists");
    }
}

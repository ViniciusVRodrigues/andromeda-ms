package andromeda.sei.ms.feature.role.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class RoleNotFoundException extends ResourceNotFoundException {

    public RoleNotFoundException(String id) {
        super("ROLE_NOT_FOUND", "Role not found: " + id);
    }
}

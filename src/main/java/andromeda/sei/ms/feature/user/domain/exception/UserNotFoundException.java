package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(String id) {
        super("USER_NOT_FOUND", "User not found: " + id);
    }
}

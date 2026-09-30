package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** The user was changed by someone else since it was read (optimistic locking on {@code version}). */
public class UserConflictException extends ResourceConflictException {

    public UserConflictException(String id) {
        super("USER_CONFLICT", "User was modified concurrently: " + id);
    }
}

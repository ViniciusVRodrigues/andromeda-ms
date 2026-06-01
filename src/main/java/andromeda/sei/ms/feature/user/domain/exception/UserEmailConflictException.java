package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

public class UserEmailConflictException extends ResourceConflictException {

    public UserEmailConflictException(String email) {
        super("USER_EMAIL_CONFLICT", "Email already in use: " + email);
    }
}

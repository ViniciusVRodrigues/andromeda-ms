package andromeda.sei.ms.feature.auth.domain.exception;

import andromeda.sei.ms.shared.domain.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password");
    }
}

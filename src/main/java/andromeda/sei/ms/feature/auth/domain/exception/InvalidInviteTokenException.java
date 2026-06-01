package andromeda.sei.ms.feature.auth.domain.exception;

import andromeda.sei.ms.shared.domain.exception.UnauthorizedException;

public class InvalidInviteTokenException extends UnauthorizedException {

    public InvalidInviteTokenException() {
        super("INVALID_INVITE_TOKEN", "Invite token is invalid or has already been used");
    }
}

package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * The caller tried to deactivate their own account. The UI already hides the action; the server
 * refuses it too so a lone admin cannot lock the organization out.
 */
public class UserSelfDeactivationDeniedException extends ForbiddenOperationException {

    public UserSelfDeactivationDeniedException(String userId) {
        super("USER_SELF_DEACTIVATION_DENIED", "Users cannot deactivate themselves: " + userId);
    }
}

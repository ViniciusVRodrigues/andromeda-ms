package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * The caller tried to change their own role. Permissions are resolved on the server on every
 * request, so a lone admin demoting themselves would lose {@code USER_MANAGE} immediately and lock
 * the organization out of user management. Another holder of {@code USER_MANAGE} must make the
 * change — same rationale as {@link PermissionSelfEditDeniedException}.
 */
public class UserSelfRoleChangeDeniedException extends ForbiddenOperationException {

    public UserSelfRoleChangeDeniedException(String userId) {
        super("USER_SELF_ROLE_CHANGE_DENIED", "Users cannot change their own role: " + userId);
    }
}

package andromeda.sei.ms.feature.user.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * The caller tried to replace their own permission overrides. Self-edits are refused because the
 * escalation guard cannot tell a legitimate self-restriction from a lock-out: once a caller denies
 * themselves a code they could never lift the deny again, and a lone admin denying
 * {@code USER_MANAGE} would lock the whole organization. Another holder of {@code USER_MANAGE}
 * must make the change (project-scoped-permissions, "Escalation guard").
 */
public class PermissionSelfEditDeniedException extends ForbiddenOperationException {

    public PermissionSelfEditDeniedException(String userId) {
        super("PERMISSION_SELF_EDIT_DENIED",
                "Users cannot change their own permission overrides: " + userId);
    }
}

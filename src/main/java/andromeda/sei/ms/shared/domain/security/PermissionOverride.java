package andromeda.sei.ms.shared.domain.security;

import java.time.Instant;
import java.util.Objects;

/**
 * Per-user permission override (project-scoped-permissions D2): embedded in the user document.
 *
 * @param code      permission code
 * @param effect    {@code GRANT} or {@code DENY}
 * @param projectId {@code null} for an organization-level override; otherwise the project it
 *                  applies to (only allowed for {@link PermissionScope#PROJECT_SCOPABLE} codes)
 * @param updatedBy user id of whoever last wrote this entry
 * @param updatedAt when this entry was last written
 */
public record PermissionOverride(String code, PermissionEffect effect, String projectId,
                                 String updatedBy, Instant updatedAt) {

    public PermissionOverride {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(effect, "effect");
    }

    public boolean isOrganizationLevel() {
        return projectId == null;
    }

    /** Identity of the entry for diffs: same code, effect and scope, regardless of audit fields. */
    public Key key() {
        return new Key(code, effect, projectId);
    }

    public record Key(String code, PermissionEffect effect, String projectId) {}
}

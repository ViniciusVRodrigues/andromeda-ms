package andromeda.sei.ms.feature.user.presentation.dto;

import java.util.List;

/**
 * Full replacement of a user's override list.
 *
 * @param version the {@code version} returned by {@code GET /users/{id}/permissions}; a stale one
 *                answers {@code 409 USER_CONFLICT}
 */
public record ReplacePermissionOverridesRequest(List<PermissionOverrideRequest> overrides, Long version) {}

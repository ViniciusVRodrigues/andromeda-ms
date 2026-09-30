package andromeda.sei.ms.feature.user.application.dto;

import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;

import java.util.List;

/**
 * @param caller   effective permissions of whoever is writing (escalation guard)
 * @param version  the user's version read by the client; {@code null} skips the stale-read check
 */
public record ReplacePermissionOverridesInput(
        UserPermissionSnapshot caller,
        String userId,
        List<PermissionOverrideInput> overrides,
        Long version
) {}

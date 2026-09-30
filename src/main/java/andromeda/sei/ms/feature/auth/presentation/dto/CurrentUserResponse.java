package andromeda.sei.ms.feature.auth.presentation.dto;

import java.util.List;
import java.util.Map;

public record CurrentUserResponse(
        String userId,
        String name,
        String email,
        String organizationId,
        String orgName,
        String orgSlug,
        /** Same as {@code orgPermissions}, kept for one release (project-scoped-permissions 8.1). */
        List<String> permissions,
        List<String> orgPermissions,
        Map<String, List<String>> projectPermissions
) {}

package andromeda.sei.ms.feature.auth.presentation.dto;

import java.util.List;

public record CurrentUserResponse(
        String userId,
        String name,
        String email,
        String organizationId,
        String orgName,
        String orgSlug,
        List<String> permissions
) {}

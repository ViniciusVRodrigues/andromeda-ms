package andromeda.sei.ms.feature.auth.application.dto;

import java.util.List;

public record GetCurrentUserOutput(
        String userId,
        String name,
        String email,
        String organizationId,
        String orgName,
        String orgSlug,
        List<String> permissions
) {}

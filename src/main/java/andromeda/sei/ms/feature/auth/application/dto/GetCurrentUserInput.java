package andromeda.sei.ms.feature.auth.application.dto;

import java.util.List;

public record GetCurrentUserInput(
        String userId,
        String organizationId,
        List<String> permissionCodes
) {}

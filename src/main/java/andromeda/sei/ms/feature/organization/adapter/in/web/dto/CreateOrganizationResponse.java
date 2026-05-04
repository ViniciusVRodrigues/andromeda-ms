package andromeda.sei.ms.feature.organization.adapter.in.web.dto;

import java.util.UUID;

public record CreateOrganizationResponse(
        UUID organizationId,
        String slug,
        UUID adminUserId,
        String accessToken
) {}

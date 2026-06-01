package andromeda.sei.ms.feature.organization.presentation.dto;

import java.time.Instant;

public record OrganizationResponse(
        String id,
        String name,
        String slug,
        Instant createdAt
) {
}

package andromeda.sei.ms.feature.organization.presentation.dto;

import java.time.Instant;
import java.util.List;

public record OrganizationResponse(
        String id,
        String name,
        String slug,
        String sector,
        String country,
        List<String> seniorities,
        List<AiPriceEntryResponse> aiPrices,
        Instant createdAt
) {
}

package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.Organization;

import java.time.Instant;
import java.util.List;

public record CreateOrganizationOutput(
        String id,
        String name,
        String slug,
        String sector,
        String country,
        List<String> seniorities,
        List<AiPriceEntry> aiPrices,
        Instant createdAt
) {

    public static CreateOrganizationOutput from(Organization org) {
        return new CreateOrganizationOutput(org.getId(), org.getName(), org.getSlug(),
                org.getSector(), org.getCountry(), org.getSeniorities(),
                org.getAiPrices(), org.getCreatedAt());
    }
}

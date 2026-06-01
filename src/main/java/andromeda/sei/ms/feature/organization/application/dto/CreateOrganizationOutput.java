package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.organization.domain.Organization;

import java.time.Instant;

public record CreateOrganizationOutput(String id, String name, String slug, Instant createdAt) {

    public static CreateOrganizationOutput from(Organization org) {
        return new CreateOrganizationOutput(org.getId(), org.getName(), org.getSlug(), org.getCreatedAt());
    }
}

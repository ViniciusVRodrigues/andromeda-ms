package andromeda.sei.ms.feature.organization.presentation.dto;

public record OnboardOrganizationResponse(
        String orgId,
        String orgName,
        String orgSlug,
        String adminEmail,
        String token
) {
}

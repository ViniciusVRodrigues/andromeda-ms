package andromeda.sei.ms.feature.organization.application.dto;

import java.util.List;

public record OnboardOrganizationOutput(
        String orgId,
        String orgName,
        String orgSlug,
        String userId,
        String adminEmail,
        List<String> permissionCodes
) {
}

package andromeda.sei.ms.feature.organization.application.dto;

import java.util.List;

public record OnboardOrganizationOutput(
        String orgId,
        String orgName,
        String orgSlug,
        String userId,
        String adminEmail,
        String projectId,
        String projectName,
        int inviteCount,
        List<String> permissionCodes
) {
}

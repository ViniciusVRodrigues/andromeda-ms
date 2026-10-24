package andromeda.sei.ms.feature.auth.application.dto;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;

import java.util.List;

public record IssueOAuthLinkTicketInput(
        String userId,
        String organizationId,
        List<String> permissionCodes,
        OAuthLinkPurpose purpose,
        String azureOrgUrl
) {}

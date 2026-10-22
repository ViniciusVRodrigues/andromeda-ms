package andromeda.sei.ms.feature.auth.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GetCurrentUserOutput(
        String userId,
        String name,
        String email,
        String organizationId,
        String orgName,
        String orgSlug,
        List<String> permissions,
        List<String> orgPermissions,
        Map<String, List<String>> projectPermissions,
        String roleId,
        String roleLabel,
        String seniority,
        Instant memberSince
) {}

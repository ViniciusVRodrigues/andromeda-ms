package andromeda.sei.ms.feature.user.application.dto;

import java.util.List;

/**
 * A project member with their effective project-scopable codes on that project and the
 * project-level overrides that apply to it (project member panel).
 */
public record ProjectMemberPermissionsOutput(
        String userId,
        String name,
        String email,
        String roleName,
        List<String> effective,
        List<ProjectOverride> projectOverrides
) {
    public record ProjectOverride(String code, String effect) {}
}

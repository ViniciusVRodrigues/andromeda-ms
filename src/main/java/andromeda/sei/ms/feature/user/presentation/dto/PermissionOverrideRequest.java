package andromeda.sei.ms.feature.user.presentation.dto;

/**
 * @param effect    {@code GRANT} or {@code DENY}
 * @param projectId {@code null} for an organization-level override
 */
public record PermissionOverrideRequest(String code, String effect, String projectId) {}

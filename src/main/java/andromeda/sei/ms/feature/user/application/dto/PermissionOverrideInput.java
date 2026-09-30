package andromeda.sei.ms.feature.user.application.dto;

/**
 * @param effect    {@code GRANT} or {@code DENY}
 * @param projectId {@code null} for an organization-level override
 */
public record PermissionOverrideInput(String code, String effect, String projectId) {}

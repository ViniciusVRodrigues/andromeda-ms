package andromeda.sei.ms.shared.application.security;

/**
 * A user's role, overrides or active state changed (project-scoped-permissions D5): their cached
 * effective permissions must be recomputed on the next request.
 */
public record UserPermissionsChanged(String organizationId, String userId) {}

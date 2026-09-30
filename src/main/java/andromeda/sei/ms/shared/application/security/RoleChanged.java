package andromeda.sei.ms.shared.application.security;

/**
 * A role's codes changed or the role was removed (project-scoped-permissions D5). A default role
 * has a {@code null} organization and affects every organization.
 */
public record RoleChanged(String roleId, String organizationId) {}

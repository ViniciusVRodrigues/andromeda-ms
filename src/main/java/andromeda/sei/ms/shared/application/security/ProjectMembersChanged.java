package andromeda.sei.ms.shared.application.security;

/**
 * A project's members changed, or the project was created or deactivated
 * (project-scoped-permissions D5): memberships feed the effective project permissions.
 */
public record ProjectMembersChanged(String organizationId, String projectId) {}

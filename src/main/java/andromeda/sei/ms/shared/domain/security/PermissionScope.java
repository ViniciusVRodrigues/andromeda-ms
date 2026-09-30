package andromeda.sei.ms.shared.domain.security;

/**
 * Scope of a permission code (project-scoped-permissions, permission-scopes).
 *
 * <ul>
 *   <li>{@link #ORG_ONLY}: management, configuration, tokens and own data — only meaningful at
 *       organization level; never overridden per project.</li>
 *   <li>{@link #PROJECT_SCOPABLE}: project data reads and team/strategic dashboards — evaluated
 *       project by project (membership, {@code PROJECT_ACCESS_ALL} or a project-level grant).</li>
 * </ul>
 */
public enum PermissionScope {
    ORG_ONLY,
    PROJECT_SCOPABLE
}

package andromeda.sei.ms.shared.domain.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Source of truth for the scope of every permission code (project-scoped-permissions,
 * permission-scopes). The seeder, the catalog migration, the resolver and {@code GET
 * /api/v1/permissions} all read from here.
 *
 * <p>Two codes are not in the spec's table and are classified by their actual use:
 * {@code PROJECT_MANAGE} guards project create/edit/deactivate and is a management code
 * ({@link PermissionScope#ORG_ONLY});
 * {@code WORK_ITEM_MANAGE} guards the manual edit of a work item, which belongs to one project, so
 * it is {@link PermissionScope#PROJECT_SCOPABLE}.
 */
public final class PermissionCatalog {

    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String PROJECT_ACCESS_ALL = "PROJECT_ACCESS_ALL";
    public static final String PROJECT_MANAGE = "PROJECT_MANAGE";
    public static final String ORGANIZATION_DELETE = "ORGANIZATION_DELETE";
    public static final String AZURE_READ = "AZURE_READ";
    public static final String REPOS_READ = "REPOS_READ";
    public static final String DASHBOARD_TEAM = "DASHBOARD_TEAM";
    public static final String DASHBOARD_STRATEGIC = "DASHBOARD_STRATEGIC";
    public static final String AI_READ_ALL = "AI_READ_ALL";
    public static final String WORK_ITEM_MANAGE = "WORK_ITEM_MANAGE";

    /** Codes only the global ADMIN preset holds: never assignable to (or cloned into) organization roles. */
    public static final java.util.Set<String> NON_ASSIGNABLE_TO_ROLES = java.util.Set.of(ORGANIZATION_DELETE);

    private static final Map<String, PermissionScope> SCOPES = build();

    private PermissionCatalog() {}

    public static boolean isAssignableToRole(String code) {
        return !NON_ASSIGNABLE_TO_ROLES.contains(code);
    }

    private static Map<String, PermissionScope> build() {
        Map<String, PermissionScope> scopes = new LinkedHashMap<>();
        for (String code : new String[]{USER_MANAGE, "ROLE_MANAGE", "PRICE_TABLE_MANAGE", "AZURE_CONFIGURE",
                "REPOS_CONFIGURE", "CONNECTOR_TOKEN_MANAGE_OWN", "CONNECTOR_TOKEN_MANAGE_ALL", "AI_READ_OWN",
                "DASHBOARD_INDIVIDUAL", PROJECT_ACCESS_ALL, PROJECT_MANAGE, ORGANIZATION_DELETE}) {
            scopes.put(code, PermissionScope.ORG_ONLY);
        }
        for (String code : new String[]{AZURE_READ, REPOS_READ, DASHBOARD_TEAM, DASHBOARD_STRATEGIC, AI_READ_ALL,
                WORK_ITEM_MANAGE}) {
            scopes.put(code, PermissionScope.PROJECT_SCOPABLE);
        }
        return Collections.unmodifiableMap(scopes);
    }

    /** Every known code with its scope, in a stable order. */
    public static Map<String, PermissionScope> scopes() {
        return SCOPES;
    }

    public static Set<String> codes() {
        return SCOPES.keySet();
    }

    public static boolean isKnown(String code) {
        return code != null && SCOPES.containsKey(code);
    }

    public static Optional<PermissionScope> scopeOf(String code) {
        return Optional.ofNullable(code == null ? null : SCOPES.get(code));
    }

    /** Unknown codes (legacy, retired) are treated as organization-only: never widened per project. */
    public static boolean isProjectScopable(String code) {
        return scopeOf(code).orElse(PermissionScope.ORG_ONLY) == PermissionScope.PROJECT_SCOPABLE;
    }
}

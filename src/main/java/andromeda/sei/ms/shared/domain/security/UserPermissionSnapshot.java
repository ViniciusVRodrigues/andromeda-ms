package andromeda.sei.ms.shared.domain.security;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything needed to decide a user's permissions, loaded once per user and cached
 * (project-scoped-permissions D5): the role preset, the user's overrides and the active projects
 * they are a member of. Immutable.
 */
public final class UserPermissionSnapshot {

    private final String organizationId;
    private final String userId;
    private final String roleId;
    private final String roleName;
    private final List<String> roleCodes;
    private final List<PermissionOverride> overrides;
    private final Long userVersion;
    private final PermissionResolver resolver;

    public UserPermissionSnapshot(String organizationId, String userId, String roleId, String roleName,
                                  List<String> roleCodes, List<PermissionOverride> overrides,
                                  Set<String> memberProjectIds, Long userVersion) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleCodes = List.copyOf(PermissionAliasResolver.canonicalize(roleCodes));
        this.overrides = overrides == null ? List.of() : List.copyOf(overrides);
        this.userVersion = userVersion;
        this.resolver = new PermissionResolver(this.roleCodes, this.overrides, memberProjectIds);
    }

    public String organizationId() { return organizationId; }
    public String userId() { return userId; }
    public String roleId() { return roleId; }
    public String roleName() { return roleName; }
    public List<String> roleCodes() { return roleCodes; }
    public List<PermissionOverride> overrides() { return overrides; }
    public Long userVersion() { return userVersion; }
    public PermissionResolver resolver() { return resolver; }
}

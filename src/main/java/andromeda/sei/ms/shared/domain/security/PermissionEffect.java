package andromeda.sei.ms.shared.domain.security;

/** Effect of a per-user permission override. Within the same level {@link #DENY} wins. */
public enum PermissionEffect {
    GRANT,
    DENY
}

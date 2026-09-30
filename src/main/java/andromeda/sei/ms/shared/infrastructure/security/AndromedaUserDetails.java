package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal. {@code permissions} are the effective organization-level codes resolved
 * on the server for this request (project-scoped-permissions D5), never the JWT claims;
 * {@code permissionSnapshot} carries what the {@code perm} evaluator needs for project checks.
 */
@Builder
public class AndromedaUserDetails implements UserDetails {

    private final String userId;
    private final String organizationId;
    private final List<String> permissions;
    private final UserPermissionSnapshot permissionSnapshot;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return userId;
    }

    public String getUserId() {
        return userId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    /** {@code null} when the principal was built without one (tests); resolve it again then. */
    public UserPermissionSnapshot getPermissionSnapshot() {
        return permissionSnapshot;
    }
}

package andromeda.sei.ms.shared.infrastructure.security;

import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Builder
public class AndromedaUserDetails implements UserDetails {

    private final String userId;
    private final String organizationId;
    private final List<String> permissions;

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
}

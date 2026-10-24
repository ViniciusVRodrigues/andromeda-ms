package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Effective permissions of the authenticated caller (project-scoped-permissions D5): the snapshot
 * the JWT filter resolved for this request, or a fresh (cached) lookup when the principal carries
 * none. Controllers hand it to use cases that filter listings by accessible projects.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserPermissions {

    private final EffectivePermissionService effectivePermissionService;

    public Optional<UserPermissionSnapshot> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AndromedaUserDetails user)) return Optional.empty();
        if (user.getPermissionSnapshot() != null) return Optional.of(user.getPermissionSnapshot());
        return effectivePermissionService.snapshot(user.getOrganizationId(), user.getUserId());
    }

    public UserPermissionSnapshot require() {
        return current().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("No authenticated user with effective permissions"));
    }

    /**
     * Whether the caller may read {@code targetUserId}'s personal data (AI usage, allocations) — project-scoped-permissions 6.4: themselves, or a member of a project where the
     * caller holds {@code AI_READ_ALL} or {@code DASHBOARD_TEAM} (the codes that already allowed it).
     */
    public boolean canReadUserData(String targetUserId) {
        return effectivePermissionService.canReadUserData(require(), targetUserId, TEAM_DATA_CODES);
    }

    /** Users whose personal data the caller may read; empty means the whole organization. */
    public Optional<Set<String>> visibleUserIds() {
        return effectivePermissionService.visibleUserIds(require(), TEAM_DATA_CODES);
    }

    private static final List<String> TEAM_DATA_CODES =
            List.of(PermissionCatalog.AI_READ_ALL, PermissionCatalog.DASHBOARD_TEAM);
}

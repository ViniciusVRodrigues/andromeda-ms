package andromeda.sei.ms.feature.dashboardlayout.infrastructure.security;

import andromeda.sei.ms.feature.dashboardlayout.application.DashboardViewAccess;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.infrastructure.security.ProjectPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@code dashboardLayoutAccess} bean of the layout endpoints' {@code @PreAuthorize}: the single
 * map view → permission of design.md D3.
 * <ul>
 *     <li>{@code individual} → {@code hasAuthority('DASHBOARD_INDIVIDUAL')};</li>
 *     <li>{@code equipe} → {@code @perm.onAnyProject('DASHBOARD_TEAM')};</li>
 *     <li>{@code projeto} and {@code org} → {@code @perm.onAnyProject('DASHBOARD_STRATEGIC')}.</li>
 * </ul>
 * An unknown viewKey throws {@code DashboardLayoutViewUnknownException} from inside the expression;
 * SpEL rethrows it unwrapped, so the client gets 422 before any 403 (same mechanism as the
 * {@code perm} bean's 404s).
 */
@Component("dashboardLayoutAccess")
@RequiredArgsConstructor
public class DashboardLayoutAccess implements DashboardViewAccess {

    static final String DASHBOARD_INDIVIDUAL = "DASHBOARD_INDIVIDUAL";

    private final ProjectPermissionEvaluator perm;

    /** Whether the caller may save or restore the layout of {@code viewKey}; 422 when unknown. */
    public boolean canAccess(String viewKey) {
        return allows(DashboardViewKey.parse(viewKey), new HashMap<>());
    }

    /** Wire values of the views the caller may see, to filter {@code GET /layouts}. */
    @Override
    public Set<String> accessibleViewKeys() {
        Map<String, Boolean> anyProject = new HashMap<>();
        return Arrays.stream(DashboardViewKey.values())
                .filter(key -> allows(key, anyProject))
                .map(DashboardViewKey::value)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean allows(DashboardViewKey key, Map<String, Boolean> anyProject) {
        return switch (key) {
            case INDIVIDUAL -> hasAuthority(DASHBOARD_INDIVIDUAL);
            case EQUIPE -> onAnyProject(PermissionCatalog.DASHBOARD_TEAM, anyProject);
            case PROJETO, ORG -> onAnyProject(PermissionCatalog.DASHBOARD_STRATEGIC, anyProject);
        };
    }

    private boolean onAnyProject(String code, Map<String, Boolean> memo) {
        return memo.computeIfAbsent(code, perm::onAnyProject);
    }

    private static boolean hasAuthority(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(code::equals);
    }
}

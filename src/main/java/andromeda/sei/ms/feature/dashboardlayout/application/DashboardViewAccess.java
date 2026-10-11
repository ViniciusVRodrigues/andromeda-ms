package andromeda.sei.ms.feature.dashboardlayout.application;

import java.util.Set;

/**
 * Port: which customizable views the authenticated caller may see (design.md D3). Implemented in
 * infrastructure from the caller's effective permissions.
 */
public interface DashboardViewAccess {

    /** Wire values ({@code individual}, {@code equipe}, {@code projeto}, {@code org}) the caller may see. */
    Set<String> accessibleViewKeys();
}

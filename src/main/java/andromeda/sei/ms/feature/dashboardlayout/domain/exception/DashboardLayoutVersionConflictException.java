package andromeda.sei.ms.feature.dashboardlayout.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * The client's {@code version} does not match the stored layout (another tab saved first, or a
 * create raced an existing layout): 409, the client reloads and reapplies (design.md D6).
 */
public class DashboardLayoutVersionConflictException extends ResourceConflictException {

    public DashboardLayoutVersionConflictException(String viewKey) {
        super("DASHBOARD_LAYOUT_VERSION_CONFLICT", "Dashboard layout changed since it was read: " + viewKey);
    }
}

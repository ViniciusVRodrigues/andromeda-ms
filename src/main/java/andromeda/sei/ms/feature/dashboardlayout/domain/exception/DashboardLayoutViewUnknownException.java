package andromeda.sei.ms.feature.dashboardlayout.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** The {@code viewKey} is not one of the customizable views (design.md D3/D6): 422. */
public class DashboardLayoutViewUnknownException extends BusinessRuleException {

    public DashboardLayoutViewUnknownException(String viewKey) {
        super("DASHBOARD_LAYOUT_VIEW_UNKNOWN", "Unknown dashboard view: " + viewKey);
    }
}

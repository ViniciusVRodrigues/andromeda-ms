package andromeda.sei.ms.feature.dashboardlayout.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** The layout breaks a structural rule (id format, duplicates, size, item limit): 422. */
public class DashboardLayoutInvalidException extends BusinessRuleException {

    public DashboardLayoutInvalidException(String reason) {
        super("DASHBOARD_LAYOUT_INVALID", "Invalid dashboard layout: " + reason);
    }
}

package andromeda.sei.ms.feature.dashboard.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** {@code periodDays} outside the allowed set of the v2 project dashboards (422). */
public class DashboardPeriodInvalidException extends BusinessRuleException {

    public static final String CODE = "DASHBOARD_PERIOD_INVALID";

    public DashboardPeriodInvalidException(String periodDays) {
        super(CODE, "periodDays must be one of 30, 90, 180 or 365: " + periodDays);
    }
}

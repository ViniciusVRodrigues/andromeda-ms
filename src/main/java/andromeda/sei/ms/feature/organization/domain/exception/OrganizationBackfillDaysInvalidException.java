package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class OrganizationBackfillDaysInvalidException extends BusinessRuleException {

    public OrganizationBackfillDaysInvalidException(int backfillDays) {
        super("ORGANIZATION_BACKFILL_DAYS_INVALID",
                "backfillDays must be between " + SyncSettings.MIN_BACKFILL_DAYS + " and "
                        + SyncSettings.MAX_BACKFILL_DAYS + ": " + backfillDays);
    }
}

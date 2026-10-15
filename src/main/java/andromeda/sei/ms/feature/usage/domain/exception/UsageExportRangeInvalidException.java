package andromeda.sei.ms.feature.usage.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** Export period with {@code to} before {@code from}, or longer than 366 days (HTTP 422). */
public class UsageExportRangeInvalidException extends BusinessRuleException {

    public UsageExportRangeInvalidException(String reason) {
        super("USAGE_EXPORT_RANGE_INVALID", reason);
    }
}

package andromeda.sei.ms.feature.aiusage.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

import java.time.LocalDate;

/** {@code from} after {@code to} on an AI usage query (400). */
public class AiUsagePeriodInvalidException extends DomainException {

    public static final String CODE = "AI_USAGE_PERIOD_INVALID";

    public AiUsagePeriodInvalidException(LocalDate from, LocalDate to) {
        super(CODE, "from must not be after to: " + from + " > " + to);
    }
}

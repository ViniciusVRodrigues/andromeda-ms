package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Tolerant parsing of Azure DevOps timestamps ({@code 2016-11-01T16:30:31.6655471Z}, offsets). */
final class AzureDates {

    /** Azure DevOps reports "never" as {@code 0001-01-01T00:00:00}. */
    private static final int FIRST_REAL_YEAR = 1970;

    private AzureDates() {}

    static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            Instant instant = Instant.parse(value);
            return plausible(instant);
        } catch (DateTimeParseException e) {
            try {
                return plausible(OffsetDateTime.parse(value).toInstant());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static Instant plausible(Instant instant) {
        return instant.isBefore(Instant.parse(FIRST_REAL_YEAR + "-01-01T00:00:00Z")) ? null : instant;
    }
}

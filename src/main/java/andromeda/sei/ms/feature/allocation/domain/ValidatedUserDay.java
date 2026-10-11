package andromeda.sei.ms.feature.allocation.domain;

import java.time.LocalDate;

/** A (person, day) covered by at least one active {@code VALIDATED} allocation. */
public record ValidatedUserDay(String userId, LocalDate day) {}

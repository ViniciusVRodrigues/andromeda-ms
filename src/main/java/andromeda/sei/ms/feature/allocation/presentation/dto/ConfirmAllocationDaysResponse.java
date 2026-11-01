package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.time.LocalDate;
import java.util.List;

/** {@code awaitingApproval}: days whose confirmation fell in the approval policy (not in {@code confirmed}). */
public record ConfirmAllocationDaysResponse(List<LocalDate> confirmed, List<Skipped> skipped,
                                            List<LocalDate> awaitingApproval) {

    public ConfirmAllocationDaysResponse(List<LocalDate> confirmed, List<Skipped> skipped) {
        this(confirmed, skipped, List.of());
    }

    public record Skipped(LocalDate day, String reason) {
    }
}

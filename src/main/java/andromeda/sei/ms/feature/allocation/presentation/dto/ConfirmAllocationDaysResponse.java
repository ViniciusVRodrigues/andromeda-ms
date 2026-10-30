package andromeda.sei.ms.feature.allocation.presentation.dto;

import java.time.LocalDate;
import java.util.List;

public record ConfirmAllocationDaysResponse(List<LocalDate> confirmed, List<Skipped> skipped) {

    public record Skipped(LocalDate day, String reason) {
    }
}

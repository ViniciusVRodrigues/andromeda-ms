package andromeda.sei.ms.feature.allocation.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

/** {@code day} or {@code allocationId} locates the rows; with an id the allocation's own day is used. */
public record PreviewAllocationRequest(
        LocalDate day,
        String allocationId,
        @NotEmpty List<@Valid AllocationSplitRequest> splits
) {
    @jakarta.validation.constraints.AssertTrue(message = "day or allocationId is required")
    public boolean isLocated() {
        return day != null || (allocationId != null && !allocationId.isBlank());
    }
}

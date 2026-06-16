package andromeda.sei.ms.feature.allocation.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record ManualAllocationForDayRequest(
        @NotNull LocalDate day,
        @NotEmpty List<@Valid AllocationSplitRequest> splits
) {}

package andromeda.sei.ms.feature.allocation.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ManualAllocationRequest(
        @NotEmpty List<@Valid AllocationSplitRequest> splits
) {}

package andromeda.sei.ms.feature.allocation.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnAllocationRequest(@NotBlank @Size(max = 280) String note) {}

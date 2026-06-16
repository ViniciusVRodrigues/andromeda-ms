package andromeda.sei.ms.feature.connector.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FluencyScoreCategoryRequest(
        @NotBlank @Size(max = 128) String category,
        @Size(max = 64) String icon,
        @Min(1) @Max(4) int stage,
        @Size(max = 20) List<@Size(max = 512) String> tips
) {}

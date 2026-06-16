package andromeda.sei.ms.feature.connector.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FluencyScoreRequest(
        @NotNull @Min(1) @Max(4) Integer overallStage,
        @Size(max = 128) String overallLabel,
        @Size(max = 64) String computedAt,
        @NotNull @Valid @Size(max = 100) List<FluencyScoreCategoryRequest> categories
) {}

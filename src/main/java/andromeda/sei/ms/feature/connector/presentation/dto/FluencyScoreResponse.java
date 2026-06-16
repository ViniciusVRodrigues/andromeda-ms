package andromeda.sei.ms.feature.connector.presentation.dto;

import java.util.List;

public record FluencyScoreResponse(
        int overallStage,
        String overallLabel,
        String computedAt,
        List<CategoryScoreResponse> categories
) {}

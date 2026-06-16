package andromeda.sei.ms.feature.connector.presentation.dto;

import java.util.List;

public record CategoryScoreResponse(
        String category,
        String icon,
        int stage,
        List<String> tips
) {}

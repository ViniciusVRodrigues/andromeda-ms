package andromeda.sei.ms.feature.dashboardlayout.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardWidgetPayload(
        @Schema(description = "Opaque catalog id, format ^[a-z0-9-]+\\.[a-z0-9-]+$", example = "equipe.burndown")
        String id,
        @Schema(description = "Width: P=4, M=6, G=8, I=12 columns", allowableValues = {"P", "M", "G", "I"}, example = "G")
        String size
) {
}

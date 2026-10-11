package andromeda.sei.ms.feature.dashboardlayout.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DashboardSectionPayload(
        @Schema(description = "Section id, format ^[a-z0-9-]+$", example = "fluxo")
        String id,
        @Schema(description = "Collapsed state; missing means expanded", example = "false")
        Boolean collapsed,
        @Schema(description = "Widgets of the section, in visual order")
        List<DashboardWidgetPayload> widgets
) {
}

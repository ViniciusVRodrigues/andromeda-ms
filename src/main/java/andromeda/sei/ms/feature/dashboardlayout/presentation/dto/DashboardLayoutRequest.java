package andromeda.sei.ms.feature.dashboardlayout.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Body of {@code PUT /api/v1/dashboards/layouts/{viewKey}}. There is deliberately no user or
 * organization field: both come from the JWT (design.md D6); unknown properties are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DashboardLayoutRequest(
        @Schema(description = "Sections in visual order", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DashboardSectionPayload> sections,
        @Schema(description = "Widget ids the user turned off", example = "[\"equipe.commits-dia\"]")
        List<String> hidden,
        @Schema(description = "Version last read; null creates the layout", nullable = true, example = "3")
        Long version
) {
}

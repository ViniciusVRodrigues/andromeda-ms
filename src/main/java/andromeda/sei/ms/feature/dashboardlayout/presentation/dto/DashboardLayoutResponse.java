package andromeda.sei.ms.feature.dashboardlayout.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record DashboardLayoutResponse(
        @Schema(allowableValues = {"individual", "equipe", "projeto", "org"}, example = "equipe")
        String viewKey,
        List<DashboardSectionPayload> sections,
        List<String> hidden,
        @Schema(description = "Send it back on the next PUT", example = "3")
        Long version,
        Instant updatedAt
) {
}

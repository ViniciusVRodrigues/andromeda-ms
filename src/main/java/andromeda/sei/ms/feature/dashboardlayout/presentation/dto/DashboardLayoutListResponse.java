package andromeda.sei.ms.feature.dashboardlayout.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DashboardLayoutListResponse(
        @Schema(description = "Active layouts of the caller, only for the views the caller may see")
        List<DashboardLayoutResponse> items
) {
}

package andromeda.sei.ms.feature.dashboardlayout.application.dto;

import java.time.Instant;
import java.util.List;

public record DashboardLayoutOutput(
        String viewKey,
        List<DashboardSectionData> sections,
        List<String> hidden,
        Long version,
        Instant updatedAt
) {
}

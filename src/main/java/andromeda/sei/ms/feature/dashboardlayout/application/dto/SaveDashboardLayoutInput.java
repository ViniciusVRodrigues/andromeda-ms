package andromeda.sei.ms.feature.dashboardlayout.application.dto;

import java.util.List;

/**
 * {@code organizationId} and {@code userId} always come from the authenticated principal, never
 * from the request (design.md D6). {@code version == null} means "create".
 */
public record SaveDashboardLayoutInput(
        String organizationId,
        String userId,
        String viewKey,
        List<DashboardSectionData> sections,
        List<String> hidden,
        Long version
) {
}

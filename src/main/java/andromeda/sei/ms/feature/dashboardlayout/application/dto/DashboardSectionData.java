package andromeda.sei.ms.feature.dashboardlayout.application.dto;

import java.util.List;

/** A section as exchanged with the presentation layer; a missing {@code collapsed} means expanded. */
public record DashboardSectionData(String id, Boolean collapsed, List<DashboardWidgetData> widgets) {
}

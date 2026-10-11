package andromeda.sei.ms.feature.dashboardlayout.domain;

/** A widget placed in a section. The id is opaque: the catalog lives in the frontend (design.md D3). */
public record DashboardWidget(String id, WidgetSize size) {
}

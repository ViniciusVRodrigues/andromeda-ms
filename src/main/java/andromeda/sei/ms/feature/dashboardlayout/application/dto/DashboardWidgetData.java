package andromeda.sei.ms.feature.dashboardlayout.application.dto;

/** A widget as exchanged with the presentation layer; {@code size} is the raw wire value (P, M, G, I). */
public record DashboardWidgetData(String id, String size) {
}

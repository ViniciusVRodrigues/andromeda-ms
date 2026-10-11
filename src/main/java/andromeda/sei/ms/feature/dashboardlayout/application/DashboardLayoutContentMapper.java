package andromeda.sei.ms.feature.dashboardlayout.application;

import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardLayoutOutput;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardSectionData;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardWidgetData;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardSection;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardWidget;
import andromeda.sei.ms.feature.dashboardlayout.domain.WidgetSize;

import java.util.List;

/**
 * Raw layout data ↔ domain. Nulls are passed through so the domain rules reject them with
 * {@code DASHBOARD_LAYOUT_INVALID}; an unknown size fails here with the same code.
 */
public final class DashboardLayoutContentMapper {

    private DashboardLayoutContentMapper() {
    }

    public static List<DashboardSection> toSections(List<DashboardSectionData> sections) {
        if (sections == null) return null;
        return sections.stream().map(DashboardLayoutContentMapper::toSection).toList();
    }

    public static DashboardLayoutOutput toOutput(DashboardLayout layout) {
        return new DashboardLayoutOutput(
                layout.getViewKey().value(),
                layout.getSections().stream().map(DashboardLayoutContentMapper::toData).toList(),
                layout.getHidden(),
                layout.getVersion(),
                layout.getUpdatedAt());
    }

    private static DashboardSection toSection(DashboardSectionData data) {
        if (data == null) return null;
        List<DashboardWidget> widgets = data.widgets() == null ? null
                : data.widgets().stream().map(DashboardLayoutContentMapper::toWidget).toList();
        return new DashboardSection(data.id(), Boolean.TRUE.equals(data.collapsed()), widgets);
    }

    private static DashboardWidget toWidget(DashboardWidgetData data) {
        if (data == null) return null;
        return new DashboardWidget(data.id(), WidgetSize.parse(data.size()));
    }

    private static DashboardSectionData toData(DashboardSection section) {
        return new DashboardSectionData(section.id(), section.collapsed(),
                section.widgets().stream().map(w -> new DashboardWidgetData(w.id(), w.size().name())).toList());
    }
}

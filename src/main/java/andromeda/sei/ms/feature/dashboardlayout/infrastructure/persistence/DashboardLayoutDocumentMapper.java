package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardSection;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardWidget;
import andromeda.sei.ms.feature.dashboardlayout.domain.WidgetSize;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DashboardLayoutDocumentMapper {

    public DashboardLayout toDomain(DashboardLayoutDocument doc) {
        return new DashboardLayout(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                DashboardViewKey.parse(doc.getViewKey()),
                doc.getSections() == null ? List.of() : doc.getSections().stream().map(this::toSection).toList(),
                doc.getHidden(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }

    public DashboardLayoutDocument toDocument(DashboardLayout layout) {
        return DashboardLayoutDocument.builder()
                .id(layout.getId())
                .organizationId(layout.getOrganizationId())
                .userId(layout.getUserId())
                .viewKey(layout.getViewKey().value())
                .sections(layout.getSections().stream().map(this::toSectionDocument).toList())
                .hidden(layout.getHidden())
                .active(layout.isActive())
                .version(layout.getVersion())
                .createdAt(layout.getCreatedAt())
                .updatedAt(layout.getUpdatedAt())
                .build();
    }

    private DashboardSection toSection(DashboardLayoutDocument.SectionDocument doc) {
        List<DashboardWidget> widgets = doc.getWidgets() == null ? List.of()
                : doc.getWidgets().stream().map(w -> new DashboardWidget(w.getId(), WidgetSize.parse(w.getSize()))).toList();
        return new DashboardSection(doc.getId(), doc.isCollapsed(), widgets);
    }

    private DashboardLayoutDocument.SectionDocument toSectionDocument(DashboardSection section) {
        return new DashboardLayoutDocument.SectionDocument(section.id(), section.collapsed(),
                section.widgets().stream()
                        .map(w -> new DashboardLayoutDocument.WidgetDocument(w.id(), w.size().name()))
                        .toList());
    }
}

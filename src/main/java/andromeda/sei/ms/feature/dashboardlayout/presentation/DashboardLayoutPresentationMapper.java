package andromeda.sei.ms.feature.dashboardlayout.presentation;

import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardLayoutOutput;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardSectionData;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardWidgetData;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.SaveDashboardLayoutInput;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardLayoutRequest;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardLayoutResponse;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardSectionPayload;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardWidgetPayload;
import org.springframework.stereotype.Component;

import java.util.List;

/** Request/response ↔ application DTOs. Nulls pass through: the domain rules reject them (422). */
@Component
public class DashboardLayoutPresentationMapper {

    public SaveDashboardLayoutInput toInput(String organizationId, String userId, String viewKey,
                                            DashboardLayoutRequest request) {
        return new SaveDashboardLayoutInput(organizationId, userId, viewKey,
                mapList(request.sections(), this::toSectionData), request.hidden(), request.version());
    }

    public DashboardLayoutResponse toResponse(DashboardLayoutOutput output) {
        return new DashboardLayoutResponse(output.viewKey(),
                mapList(output.sections(), this::toSectionPayload), output.hidden(), output.version(),
                output.updatedAt());
    }

    private DashboardSectionData toSectionData(DashboardSectionPayload payload) {
        return payload == null ? null : new DashboardSectionData(payload.id(), payload.collapsed(),
                mapList(payload.widgets(), w -> w == null ? null : new DashboardWidgetData(w.id(), w.size())));
    }

    private DashboardSectionPayload toSectionPayload(DashboardSectionData data) {
        return new DashboardSectionPayload(data.id(), data.collapsed(),
                mapList(data.widgets(), w -> new DashboardWidgetPayload(w.id(), w.size())));
    }

    private static <A, B> List<B> mapList(List<A> source, java.util.function.Function<A, B> mapper) {
        return source == null ? null : source.stream().map(mapper).toList();
    }
}

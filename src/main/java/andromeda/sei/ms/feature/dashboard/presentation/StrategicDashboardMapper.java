package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.dashboard.presentation.dto.DeveloperDashboardResponse;
import andromeda.sei.ms.feature.dashboard.presentation.dto.StrategicDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StrategicDashboardMapper {

    private final DeveloperDashboardMapper developerDashboardMapper;

    public StrategicDashboardResponse toResponse(StrategicDashboardOutput output) {
        List<StrategicDashboardResponse.ProjectSummaryResponse> projects = output.projects() == null ? null :
                output.projects().stream().map(this::toProjectSummary).toList();

        StrategicDashboardResponse.ProjectDetailResponse projectDetail = output.projectDetail() == null ? null :
                toProjectDetail(output.projectDetail());

        DeveloperDashboardResponse memberDetail = output.memberDetail() == null ? null :
                developerDashboardMapper.toResponse(output.memberDetail());

        return new StrategicDashboardResponse(output.level(), projects, projectDetail, memberDetail, null,
                output.delivery());
    }

    private StrategicDashboardResponse.ProjectSummaryResponse toProjectSummary(StrategicDashboardOutput.ProjectSummary s) {
        return new StrategicDashboardResponse.ProjectSummaryResponse(
                s.projectId(), s.projectName(), s.totalWorkItems(),
                s.storyPointsDelivered(), s.avgCycleTimeHours(), s.avgLeadTimeHours(),
                toDora(s.dora()), s.cycleTime(), s.leadTime(), s.delivery());
    }

    private StrategicDashboardResponse.ProjectDetailResponse toProjectDetail(StrategicDashboardOutput.ProjectDetail d) {
        List<StrategicDashboardResponse.MemberSummaryResponse> members = d.members() == null ? List.of() :
                d.members().stream().map(m -> new StrategicDashboardResponse.MemberSummaryResponse(
                        m.userId(), m.name(), m.linkedToAzure(),
                        m.storyPointsDelivered(), m.cycleTimeHours(), m.leadTimeHours(), m.workItemCount(),
                        m.cycleTime(), m.leadTime()))
                        .toList();
        return new StrategicDashboardResponse.ProjectDetailResponse(
                d.projectId(), d.projectName(), members, toDora(d.dora()), d.delivery());
    }

    private StrategicDashboardResponse.DoraMetricsResponse toDora(StrategicDashboardOutput.DoraMetrics dora) {
        if (dora == null) return null;
        return new StrategicDashboardResponse.DoraMetricsResponse(
                dora.leadTimeHours(), dora.leadTimeClassification(),
                dora.deployFrequencyPerWeek(), dora.deployFrequencyClassification(),
                dora.changeFailureRatePercent(), dora.changeFailureRateClassification(),
                dora.mttrHours(), dora.estimated());
    }
}

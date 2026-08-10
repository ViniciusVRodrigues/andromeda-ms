package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.dashboard.presentation.dto.TeamDashboardResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeamDashboardMapper {

    public TeamDashboardResponse toResponse(TeamDashboardOutput output) {
        List<TeamDashboardResponse.VelocityPointResponse> velocityTrend = output.velocityTrend() == null ? List.of() :
                output.velocityTrend().stream()
                        .map(vp -> new TeamDashboardResponse.VelocityPointResponse(
                                vp.sprintId(), vp.sprintName(), vp.storyPoints(), vp.aiCostUsd()))
                        .toList();

        List<TeamDashboardResponse.MemberMetricsResponse> members = output.members() == null ? List.of() :
                output.members().stream()
                        .map(m -> new TeamDashboardResponse.MemberMetricsResponse(
                                m.userId(), m.name(), m.avatarInitials(),
                                m.storyPointsDelivered(), m.cycleTimeHours(), m.leadTimeHours(),
                                m.aiCostUsd(), m.avgTokensPerSp(), m.workItemCount()))
                        .toList();

        List<TeamDashboardResponse.StalePrSummaryResponse> stalePrs = output.stalePrs() == null ? List.of() :
                output.stalePrs().stream()
                        .map(pr -> new TeamDashboardResponse.StalePrSummaryResponse(
                                pr.prId(), pr.title(), pr.authorName(), pr.createdAt(), pr.hoursOpen()))
                        .toList();

        return new TeamDashboardResponse(
                output.sprintId(), output.sprintName(), output.sprintStartDate(), output.sprintEndDate(),
                output.teamVelocity(), output.avgCycleTimeHours(), output.avgLeadTimeHours(),
                output.teamAiCostUsd(), velocityTrend, members, stalePrs
        );
    }
}

package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.presentation.dto.DeveloperDashboardResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeveloperDashboardMapper {

    public DeveloperDashboardResponse toResponse(DeveloperDashboardOutput output) {
        List<DeveloperDashboardResponse.WorkItemSummary> items = output.currentSprintItems() == null ? List.of() :
                output.currentSprintItems().stream()
                        .map(wi -> new DeveloperDashboardResponse.WorkItemSummary(
                                wi.getId(),
                                wi.getTitle(),
                                wi.getResolvedStatus(),
                                wi.getStoryPoints(),
                                wi.getCycleTimeHours(),
                                wi.getLeadTimeHours(),
                                wi.getTokensPerSp()
                        ))
                        .toList();

        List<DeveloperDashboardResponse.WorkItemSummaryDto> inProgressItems =
                output.inProgressItems() == null ? List.of() :
                output.inProgressItems().stream()
                        .map(wi -> new DeveloperDashboardResponse.WorkItemSummaryDto(
                                wi.getId(),
                                wi.getTitle(),
                                wi.getResolvedType(),
                                wi.getStoryPoints(),
                                wi.getExternalId()
                        ))
                        .toList();

        List<DeveloperDashboardResponse.DailyUsageResponse> tokensByDay = output.tokensByDay() == null ? List.of() :
                output.tokensByDay().stream()
                        .map(d -> new DeveloperDashboardResponse.DailyUsageResponse(
                                d.day(),
                                d.inputTokens(),
                                d.outputTokens(),
                                d.interactions(),
                                d.costUsd()
                        ))
                        .toList();

        List<DeveloperDashboardResponse.BurndownPointResponse> burndownSeries =
                output.burndownSeries() == null ? List.of() :
                output.burndownSeries().stream()
                        .map(bp -> new DeveloperDashboardResponse.BurndownPointResponse(
                                bp.date(), bp.remainingSp(), bp.idealSp()))
                        .toList();

        DeveloperDashboardResponse.AiSprintSummaryResponse aiSprintSummary = null;
        if (output.aiSprintSummary() != null) {
            aiSprintSummary = new DeveloperDashboardResponse.AiSprintSummaryResponse(
                    output.aiSprintSummary().totalTokens(),
                    output.aiSprintSummary().totalCostUsd(),
                    output.aiSprintSummary().unallocatedDaysCount()
            );
        }

        return new DeveloperDashboardResponse(
                items,
                output.avgCycleTimeHours(),
                output.avgLeadTimeHours(),
                output.storyPointsDelivered(),
                tokensByDay,
                output.sprintCostUsd(),
                output.avgTokensPerSp(),
                output.openPrs(),
                output.mergedPrs(),
                inProgressItems,
                output.totalSprintItems(),
                burndownSeries,
                aiSprintSummary,
                output.aiCostByWorkItemId()
        );
    }
}

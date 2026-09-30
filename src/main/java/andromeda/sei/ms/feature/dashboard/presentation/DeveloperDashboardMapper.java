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
                                wi.getEffort(),
                                wi.getCycleTimeHours(),
                                wi.getLeadTimeHours(),
                                wi.getTokensPerSp(),
                                wi.getComplexity(),
                                wi.getComplexitySource() != null ? wi.getComplexitySource().name() : null,
                                wi.getEffort(),
                                wi.getEffortSource() != null ? wi.getEffortSource().name() : null,
                                wi.getResolvedType(),
                                wi.getExternalId()
                        ))
                        .toList();

        List<DeveloperDashboardResponse.WorkItemSummaryDto> inProgressItems =
                output.inProgressItems() == null ? List.of() :
                output.inProgressItems().stream()
                        .map(wi -> new DeveloperDashboardResponse.WorkItemSummaryDto(
                                wi.getId(),
                                wi.getTitle(),
                                wi.getResolvedType(),
                                wi.getEffort(),
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

        List<DeveloperDashboardResponse.SprintHistoryResponse> sprintHistory =
                output.sprintHistory() == null ? List.of() :
                output.sprintHistory().stream()
                        .map(p -> new DeveloperDashboardResponse.SprintHistoryResponse(
                                p.sprintId(), p.name(), p.startDate(), p.endDate(),
                                p.spPlanned(), p.spDelivered(), p.avgCycleTimeHours(),
                                p.totalTokens(), p.totalCostUsd(), p.cycleTimeP50()))
                        .toList();

        return new DeveloperDashboardResponse(
                items,
                output.avgCycleTimeHours(),
                output.avgLeadTimeHours(),
                output.storyPointsDelivered(),
                output.totalSpInSprint(),
                tokensByDay,
                output.sprintCostUsd(),
                output.avgTokensPerSp(),
                output.openPrs(),
                output.mergedPrs(),
                inProgressItems,
                output.totalSprintItems(),
                burndownSeries,
                aiSprintSummary,
                output.aiCostByWorkItemId(),
                output.subscriptionCostPerUserUsd(),
                sprintHistory,
                output.sprintContext(),
                null,
                output.cycleTime(),
                output.leadTime()
        );
    }
}

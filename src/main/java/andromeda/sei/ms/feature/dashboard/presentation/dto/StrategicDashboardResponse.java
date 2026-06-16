package andromeda.sei.ms.feature.dashboard.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record StrategicDashboardResponse(
        String level,
        List<ProjectSummaryResponse> projects,
        ProjectDetailResponse projectDetail,
        DeveloperDashboardResponse memberDetail
) {
    public record ProjectSummaryResponse(
            String projectId,
            String projectName,
            int totalWorkItems,
            double storyPointsDelivered,
            Double avgCycleTimeHours,
            Double avgLeadTimeHours,
            DoraMetricsResponse dora
    ) {}

    public record ProjectDetailResponse(
            String projectId,
            String projectName,
            List<MemberSummaryResponse> members,
            DoraMetricsResponse dora
    ) {}

    public record MemberSummaryResponse(
            String userId,
            String name,
            boolean linkedToAzure,
            double storyPointsDelivered,
            Double cycleTimeHours,
            Double leadTimeHours,
            int workItemCount
    ) {}

    public record DoraMetricsResponse(
            Double leadTimeHours,
            String leadTimeClassification,
            Double deployFrequencyPerWeek,
            String deployFrequencyClassification,
            Double changeFailureRatePercent,
            String changeFailureRateClassification,
            Double mttrHours,
            boolean estimated
    ) {}
}

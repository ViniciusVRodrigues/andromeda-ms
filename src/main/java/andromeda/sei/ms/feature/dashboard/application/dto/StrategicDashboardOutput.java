package andromeda.sei.ms.feature.dashboard.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record StrategicDashboardOutput(
        String level,
        List<ProjectSummary> projects,
        ProjectDetail projectDetail,
        DeveloperDashboardOutput memberDetail
) {
    public record ProjectSummary(
            String projectId,
            String projectName,
            int totalWorkItems,
            double storyPointsDelivered,
            Double avgCycleTimeHours,
            Double avgLeadTimeHours,
            DoraMetrics dora
    ) {}

    public record ProjectDetail(
            String projectId,
            String projectName,
            List<MemberSummary> members,
            DoraMetrics dora
    ) {}

    public record MemberSummary(
            String userId,
            String name,
            boolean linkedToAzure,
            double storyPointsDelivered,
            Double cycleTimeHours,
            Double leadTimeHours,
            int workItemCount
    ) {}

    public record DoraMetrics(
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

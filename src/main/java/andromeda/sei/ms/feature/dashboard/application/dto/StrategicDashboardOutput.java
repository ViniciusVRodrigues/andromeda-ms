package andromeda.sei.ms.feature.dashboard.application.dto;

import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;

import java.util.List;

/**
 * Strategic dashboard. {@code delivery} (delivery-metrics) replaces the {@code dora} block, which
 * is kept for one release but derived from the same calculator ({@code changeFailureRatePercent =
 * changeFailRatePercent}, a percentage). At organization level {@code delivery} is computed over
 * the union of the projects' data, never as an average of project values.
 */
public record StrategicDashboardOutput(
        String level,
        List<ProjectSummary> projects,
        ProjectDetail projectDetail,
        DeveloperDashboardOutput memberDetail,
        DeliveryMetrics delivery
) {
    public StrategicDashboardOutput(String level, List<ProjectSummary> projects, ProjectDetail projectDetail,
                                    DeveloperDashboardOutput memberDetail) {
        this(level, projects, projectDetail, memberDetail, null);
    }

    public record ProjectSummary(
            String projectId,
            String projectName,
            int totalWorkItems,
            double storyPointsDelivered,
            Double avgCycleTimeHours,
            Double avgLeadTimeHours,
            DoraMetrics dora,
            DurationStat cycleTime,
            DurationStat leadTime,
            DeliveryMetrics delivery
    ) {}

    public record ProjectDetail(
            String projectId,
            String projectName,
            List<MemberSummary> members,
            DoraMetrics dora,
            DeliveryMetrics delivery
    ) {
        public ProjectDetail(String projectId, String projectName, List<MemberSummary> members, DoraMetrics dora) {
            this(projectId, projectName, members, dora, null);
        }
    }

    public record MemberSummary(
            String userId,
            String name,
            boolean linkedToAzure,
            double storyPointsDelivered,
            Double cycleTimeHours,
            Double leadTimeHours,
            int workItemCount,
            DurationStat cycleTime,
            DurationStat leadTime
    ) {}

    /** Deprecated for one release: derived from {@link DeliveryMetrics}. */
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

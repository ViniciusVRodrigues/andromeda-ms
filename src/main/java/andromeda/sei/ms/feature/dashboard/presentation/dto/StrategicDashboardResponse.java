package andromeda.sei.ms.feature.dashboard.presentation.dto;

import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code delivery} replaces {@code dora} (kept one release, same values: CFR in percent). At
 * {@code level = ORG} the top-level {@code delivery} is computed over the union of all projects.
 */
public record StrategicDashboardResponse(
        String level,
        List<ProjectSummaryResponse> projects,
        ProjectDetailResponse projectDetail,
        DeveloperDashboardResponse memberDetail,
        /** Frescura dos dados do projeto; {@code null} sem {@code projectId}. */
        DataFreshness dataFreshness,
        DeliveryMetrics delivery
) {

    public StrategicDashboardResponse withDataFreshness(DataFreshness freshness) {
        return new StrategicDashboardResponse(level, projects, projectDetail, memberDetail, freshness, delivery);
    }
    public record ProjectSummaryResponse(
            String projectId,
            String projectName,
            int totalWorkItems,
            double storyPointsDelivered,
            @Schema(deprecated = true) Double avgCycleTimeHours,
            @Schema(deprecated = true) Double avgLeadTimeHours,
            @Schema(deprecated = true) DoraMetricsResponse dora,
            DurationStat cycleTime,
            DurationStat leadTime,
            DeliveryMetrics delivery
    ) {}

    public record ProjectDetailResponse(
            String projectId,
            String projectName,
            List<MemberSummaryResponse> members,
            @Schema(deprecated = true) DoraMetricsResponse dora,
            DeliveryMetrics delivery
    ) {}

    public record MemberSummaryResponse(
            String userId,
            String name,
            boolean linkedToAzure,
            double storyPointsDelivered,
            @Schema(deprecated = true) Double cycleTimeHours,
            @Schema(deprecated = true) Double leadTimeHours,
            int workItemCount,
            DurationStat cycleTime,
            DurationStat leadTime
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

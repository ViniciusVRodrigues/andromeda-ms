package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.ProjectOverviewV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.ProjectQualityV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.ProjectRoadmapV2Response;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Project use case outputs → v2 responses, adding {@code asOf} (ISO-8601) and {@code dataFreshness}. */
@Component
public class ProjectDashboardsV2Mapper {

    public ProjectOverviewV2Response toResponse(ProjectOverviewV2Output out, DataFreshness freshness) {
        return new ProjectOverviewV2Response(out.project(), out.period(), out.kpis(), out.previous(), out.dora(),
                out.throughput(), out.sprints(), out.flow(), out.quality(), out.prFlow(), out.repos(), out.ai(),
                out.people(), out.dataQuality(), iso(out.asOf()), freshness);
    }

    public ProjectRoadmapV2Response toResponse(ProjectRoadmapV2Output out, DataFreshness freshness) {
        return new ProjectRoadmapV2Response(out.period(), out.config(), out.epics(), out.features(), out.backlog(),
                out.deliveredByTag(), out.calibration(), iso(out.asOf()), freshness);
    }

    public ProjectQualityV2Response toResponse(ProjectQualityV2Output out, DataFreshness freshness) {
        return new ProjectQualityV2Response(out.period(), out.kpis(), out.bugFlow(), out.oldestOpen(), out.rework(),
                out.commitKinds(), out.commitKindTotals(), out.totals(), iso(out.asOf()), freshness);
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}

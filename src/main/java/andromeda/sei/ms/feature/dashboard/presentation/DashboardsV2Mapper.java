package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.IndividualDashboardV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.TeamDashboardV2Response;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Use case outputs → v2 responses, adding {@code asOf} (ISO-8601) and {@code dataFreshness}. */
@Component
public class DashboardsV2Mapper {

    public IndividualDashboardV2Response toResponse(IndividualDashboardV2Output out, DataFreshness freshness) {
        return new IndividualDashboardV2Response(out.sprint(), out.person(), out.kpis(), out.previous(), out.burndown(),
                out.items(), out.pullRequests(), out.reviewsGiven(), out.commitsByDay(), out.ai(), out.fluency(),
                out.history(), iso(out.asOf()), freshness);
    }

    public TeamDashboardV2Response toResponse(TeamDashboardV2Output out, DataFreshness freshness) {
        return new TeamDashboardV2Response(out.sprint(), out.team(), out.kpis(), out.previous(), out.burndown(),
                out.statusByType(), out.agingItems(), out.p85CycleHours(), out.members(), out.stalePrs(), out.repos(),
                out.commitsByDay(), out.ai(), out.history(), iso(out.asOf()), freshness);
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}

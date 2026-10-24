package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.BurndownDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.HistoryPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.Kpis;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.Person;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.ReviewsGiven;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.SprintStats;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/individual} — the {@code buildIndividual} payload of the v2 preview
 * plus {@code asOf} and {@code dataFreshness}. The nested shapes are the application records as-is.
 */
@Schema(description = "Dashboard individual v2: dados reais da pessoa autenticada num projeto e sprint")
public record IndividualDashboardV2Response(
        SprintMeta sprint,
        Person person,
        Kpis kpis,
        SprintStats previous,
        List<BurndownDay> burndown,
        List<ItemView> items,
        List<PullRequestView> pullRequests,
        ReviewsGiven reviewsGiven,
        List<CommitDay> commitsByDay,
        Ai ai,
        List<HistoryPoint> history,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados do projeto (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

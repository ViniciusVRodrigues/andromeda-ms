package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.BurndownDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.HistoryPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Kpis;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Member;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.RepoHealth;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.SprintStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.StatusByType;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.TeamInfo;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/team} — the {@code buildTeam} payload of the v2 preview plus
 * {@code asOf} and {@code dataFreshness}. The nested shapes are the application records as-is.
 */
@Schema(description = "Dashboard de equipe v2: dados reais de um projeto e sprint")
public record TeamDashboardV2Response(
        SprintMeta sprint,
        TeamInfo team,
        Kpis kpis,
        SprintStats previous,
        List<BurndownDay> burndown,
        List<StatusByType> statusByType,
        List<ItemView> agingItems,
        @Schema(description = "p85 do cycle time (h) das sprints anteriores: referência de envelhecimento") Double p85CycleHours,
        List<Member> members,
        List<PullRequestView> stalePrs,
        List<RepoHealth> repos,
        List<CommitDay> commitsByDay,
        @Schema(description = "Custo de IA alocado ao projeto; o não alocado vem à parte em unallocatedCostUsd") Ai ai,
        List<HistoryPoint> history,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados do projeto (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

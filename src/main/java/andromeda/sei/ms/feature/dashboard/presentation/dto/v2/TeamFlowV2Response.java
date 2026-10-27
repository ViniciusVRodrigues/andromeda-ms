package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.FlowDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.ReviewQueue;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.Rework;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.Scope;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StateInfo;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StateTimes;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/team/flow} — the {@code buildTeamFlow} payload of the v2 preview
 * without the blocks that need new collection, plus {@code asOf} and {@code dataFreshness}. The
 * nested shapes are the application records as-is.
 */
@Schema(description = "Fluxo da equipe v2: fluxo cumulativo, tempo por etapa, reabertos e PRs abertos de um projeto e sprint")
public record TeamFlowV2Response(
        SprintMeta sprint,
        @Schema(description = "Modelo de processo do Azure; não é gravado, sempre null") String processTemplate,
        @Schema(description = "Estados do board e categoria; não são gravados, sempre vazio") List<StateInfo> states,
        @Schema(description = "Itens da sprint por categoria (Proposed/InProgress/Completed) no fim de cada dia útil") List<FlowDay> cfd,
        @Schema(description = "Fila e andamento {p50, p90, n} em horas dos itens concluídos; resolvedHours sempre null") StateTimes stateTimes,
        @Schema(description = "Itens concluídos na sprint que depois saíram de concluído") Rework rework,
        @Schema(description = "points: complexidade ainda não concluída, só na sprint atual") Scope scope,
        @Schema(description = "PRs abertos (estado atual, null em sprint passada) e labels dos PRs criados na sprint") ReviewQueue reviewQueue,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados do projeto (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

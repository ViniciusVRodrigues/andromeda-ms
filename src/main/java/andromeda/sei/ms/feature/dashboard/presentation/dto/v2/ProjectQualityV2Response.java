package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.BugFlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.CommitKindRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.CommitKindTotal;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.Kpis;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.OpenBug;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.ReworkRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.Totals;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/projects/{projectId}/quality} — the {@code buildQuality} payload of
 * the v2 preview (without severity, closing reason and issues) plus {@code asOf} and
 * {@code dataFreshness}.
 */
@Schema(description = "Qualidade v2 do projeto: bugs, tempo para corrigir, reabertura e commits")
public record ProjectQualityV2Response(
        Period period,
        @Schema(description = "Bugs abertos, criados, fechados, tempo para corrigir, reabertura, correções") Kpis kpis,
        @Schema(description = "Bugs criados × fechados por intervalo") List<BugFlowRow> bugFlow,
        @Schema(description = "Até 8 bugs abertos mais antigos") List<OpenBug> oldestOpen,
        @Schema(description = "Itens concluídos reabertos por intervalo") List<ReworkRow> rework,
        @Schema(description = "Tipo dos commits pela mensagem por intervalo") List<CommitKindRow> commitKinds,
        @Schema(description = "Total de commits por tipo no período") List<CommitKindTotal> commitKindTotals,
        Totals totals,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados do projeto (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.DataQuality;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Dora;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.FlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.People;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.PeriodStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.PrFlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ProjectInfo;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.QualityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.RepoRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.SprintRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ThroughputRow;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/projects/{projectId}/overview} — the {@code buildProject} payload
 * of the v2 preview plus {@code asOf} and {@code dataFreshness}. Nested shapes are the application
 * records as-is.
 */
@Schema(description = "Visão geral v2 do projeto num período: entrega, DORA, PRs, repositórios, IA e cobertura")
public record ProjectOverviewV2Response(
        ProjectInfo project,
        Period period,
        @Schema(description = "Indicadores do período") PeriodStats kpis,
        @Schema(description = "Mesmos indicadores no período anterior de mesmo tamanho; stalePrs null") PeriodStats previous,
        @Schema(description = "Métricas de entrega (proxies): notas ELITE/HIGH/MEDIUM/LOW") Dora dora,
        @Schema(description = "Itens STORY/BUG/TASK concluídos por intervalo") List<ThroughputRow> throughput,
        @Schema(description = "Sprints passadas encerradas no período: comprometido × entregue") List<SprintRow> sprints,
        @Schema(description = "Lead e cycle time {p50, p90, n} em horas por intervalo") List<FlowRow> flow,
        @Schema(description = "% de bugs entre os itens concluídos por intervalo") List<QualityRow> quality,
        @Schema(description = "PRs abertos × mergeados e tempo até a 1ª review por intervalo") List<PrFlowRow> prFlow,
        @Schema(description = "Saúde por repositório vinculado") List<RepoRow> repos,
        @Schema(description = "Custo de IA alocado ao projeto; consumo × assinatura dos membros") Ai ai,
        @Schema(description = "Membros e vínculos de identidade") People people,
        @Schema(description = "Cobertura dos dados do período") DataQuality dataQuality,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados do projeto (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

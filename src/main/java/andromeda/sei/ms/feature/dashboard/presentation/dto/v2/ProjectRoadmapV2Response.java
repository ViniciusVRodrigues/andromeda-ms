package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Backlog;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Calibration;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Config;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.EpicRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.FeatureRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.TagShare;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/projects/{projectId}/roadmap} — the {@code buildRoadmap} payload of
 * the v2 preview (without the area breakdown) plus {@code asOf} and {@code dataFreshness}.
 */
@Schema(description = "Roadmap e backlog v2 do projeto: épicos, features, backlog, temas e estimativas")
public record ProjectRoadmapV2Response(
        Period period,
        @Schema(description = "Campo de esforço mapeado e nível que carrega o esforço") Config config,
        @Schema(description = "Épicos com progresso pela hierarquia e previsão pelo ritmo de 8 semanas") List<EpicRow> epics,
        @Schema(description = "Features com progresso e previsão") List<FeatureRow> features,
        @Schema(description = "Saúde do backlog (fora de sprint ou em sprint futura)") Backlog backlog,
        @Schema(description = "Esforço entregue no período por tag") List<TagShare> deliveredByTag,
        @Schema(description = "Cycle time por esforço e complexidade; ajustes manuais") Calibration calibration,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados do projeto (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

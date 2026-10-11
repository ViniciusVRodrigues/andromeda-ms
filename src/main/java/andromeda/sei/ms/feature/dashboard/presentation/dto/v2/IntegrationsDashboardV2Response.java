package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.Connections;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.PersonLinks;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.RepoLink;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.Sync;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.UnlinkedCommits;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/integrations} — the {@code mock/integrations.js} payload of the v2
 * preview (without the live repository listing, rate limit, git author and configuration) plus
 * {@code asOf} and {@code dataFreshness}. Never carries a PAT, OAuth token, connector token hash or
 * encrypted provider token.
 */
@Schema(description = "Integrações e identidades v2: conexões, pessoas × vínculos, sync e commits sem usuário; sem segredos")
public record IntegrationsDashboardV2Response(
        @Schema(description = "Azure DevOps, GitHub e conector de IA: estado, último erro, tokens") Connections connections,
        @Schema(description = "Usuários ativos e seus vínculos, com alertas") List<PersonLinks> people,
        @Schema(description = "Alertas que não são informativos") int alertCount,
        @Schema(description = "Repositórios vinculados a projetos") List<RepoLink> repos,
        @Schema(description = "Execuções de sync dos últimos 14 dias, gatilhos, problemas e hoje") Sync sync,
        @Schema(description = "Commits dos últimos 30 dias sem usuário vinculado, por login") UnlinkedCommits unlinkedCommits,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados da organização (sync mais antigo, refresh rodando)") DataFreshness dataFreshness) {}

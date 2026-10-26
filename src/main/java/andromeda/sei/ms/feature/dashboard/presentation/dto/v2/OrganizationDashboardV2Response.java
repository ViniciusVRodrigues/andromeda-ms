package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Delivery;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Engineering;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Integrations;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.OrgStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.PortfolioRow;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/v1/dashboards/organization} — the {@code buildOrganization} payload of the v2
 * preview over the caller's {@code DASHBOARD_STRATEGIC} projects, plus {@code asOf} and
 * {@code dataFreshness}. Nested shapes are the application records as-is.
 */
@Schema(description = "Visão geral v2 da organização num período, só com os projetos em que quem chama tem DASHBOARD_STRATEGIC")
public record OrganizationDashboardV2Response(
        Period period,
        @Schema(description = "Indicadores do período (projetos e pessoas só aqui)") OrgStats kpis,
        @Schema(description = "Mesmos indicadores no período anterior; projects e people null") OrgStats previous,
        @Schema(description = "Um projeto visível por linha: sprint atual, saúde e números do período") List<PortfolioRow> portfolio,
        @Schema(description = "Vazão por projeto, lead/cycle time {p50, p90, n} e mix de tipos por intervalo") Delivery delivery,
        @Schema(description = "Commits, PRs e tempo até a 1ª review por intervalo; saúde por repositório") Engineering engineering,
        @Schema(description = "Uso de IA das pessoas dos projetos visíveis; custo alocado por projeto") Ai ai,
        @Schema(description = "Estado das conexões (sem segredos), sync de 14 dias, cobertura e fontes por projeto") Integrations integrations,
        @Schema(description = "Sprints que cruzam o período, por projeto visível: IA alocada, complexidade, horas, previsibilidade, lead time") OrganizationDashboardV2Output.SprintHistory sprintHistory,
        @Schema(description = "Instante do cálculo (ISO-8601, UTC)", example = "2026-10-11T12:00:00Z") String asOf,
        @Schema(description = "Frescura dos dados dos projetos visíveis (sync mais antigo, refresh rodando); null sem projeto") DataFreshness dataFreshness) {}

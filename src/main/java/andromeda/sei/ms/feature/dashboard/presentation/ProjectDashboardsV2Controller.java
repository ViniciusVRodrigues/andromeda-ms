package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectOverviewV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectQualityV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectRoadmapV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.ProjectOverviewV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.ProjectQualityV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.ProjectRoadmapV2Response;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project data endpoints of the v2 dashboards (dashboards-customizaveis D1): one payload per
 * sub-tab of the Project view, over the last {@code periodDays} days. Authorization is
 * {@code DASHBOARD_STRATEGIC} on the project through {@code @perm} (a project of another
 * organization is 404); the period is validated afterwards (422 {@code DASHBOARD_PERIOD_INVALID}).
 */
@RestController
@RequestMapping("/api/v1/dashboards/projects/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Dashboards v2", description = "Dados reais dos dashboards v2, um payload por visão")
public class ProjectDashboardsV2Controller {

    private static final String PERIOD_DESCRIPTION = "Dias até hoje: 30, 90 (padrão), 180 ou 365";

    private final GetProjectOverviewV2UseCase getOverview;
    private final GetProjectRoadmapV2UseCase getRoadmap;
    private final GetProjectQualityV2UseCase getQuality;
    private final ProjectDashboardsV2Mapper mapper;
    private final DataFreshnessResolver dataFreshnessResolver;

    @GetMapping("/overview")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_STRATEGIC')")
    @Operation(summary = "Visão geral v2 do projeto",
            description = "Payload buildProject: indicadores do período e do anterior, DORA, vazão, sprints, fluxo, "
                    + "PRs, repositórios, IA (custo alocado; consumo × assinatura) e cobertura. Ausência de dado é "
                    + "null; durações {p50, p90, n} em horas.")
    @ApiResponse(responseCode = "200", description = "Payload da visão geral do projeto")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_STRATEGIC no projeto")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização)")
    @ApiResponse(responseCode = "422", description = "DASHBOARD_PERIOD_INVALID")
    public ResponseEntity<ProjectOverviewV2Response> overview(
            @PathVariable("projectId") String projectId,
            @Parameter(description = PERIOD_DESCRIPTION) @RequestParam(required = false) String periodDays) {
        int days = DashboardPeriod.requireValidDays(periodDays);
        String orgId = TenantContextHolder.get();
        return ResponseEntity.ok(mapper.toResponse(getOverview.execute(orgId, projectId, days),
                dataFreshnessResolver.resolve(orgId, projectId)));
    }

    @GetMapping("/roadmap")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_STRATEGIC')")
    @Operation(summary = "Roadmap e backlog v2 do projeto",
            description = "Payload buildRoadmap sem área: épicos e features pela hierarquia com previsão pelo ritmo "
                    + "das últimas 8 semanas, saúde do backlog, tags, cycle time por esforço e complexidade, ajustes "
                    + "manuais.")
    @ApiResponse(responseCode = "200", description = "Payload do roadmap do projeto")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_STRATEGIC no projeto")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização)")
    @ApiResponse(responseCode = "422", description = "DASHBOARD_PERIOD_INVALID")
    public ResponseEntity<ProjectRoadmapV2Response> roadmap(
            @PathVariable("projectId") String projectId,
            @Parameter(description = PERIOD_DESCRIPTION) @RequestParam(required = false) String periodDays) {
        int days = DashboardPeriod.requireValidDays(periodDays);
        String orgId = TenantContextHolder.get();
        return ResponseEntity.ok(mapper.toResponse(getRoadmap.execute(orgId, projectId, days),
                dataFreshnessResolver.resolve(orgId, projectId)));
    }

    @GetMapping("/quality")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_STRATEGIC')")
    @Operation(summary = "Qualidade v2 do projeto",
            description = "Payload buildQuality sem severidade, motivo e issues: bugs abertos, criados × fechados, "
                    + "tempo para corrigir {p50, p90, n}, reabertura, commits por tipo e PRs de correção.")
    @ApiResponse(responseCode = "200", description = "Payload de qualidade do projeto")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_STRATEGIC no projeto")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização)")
    @ApiResponse(responseCode = "422", description = "DASHBOARD_PERIOD_INVALID")
    public ResponseEntity<ProjectQualityV2Response> quality(
            @PathVariable("projectId") String projectId,
            @Parameter(description = PERIOD_DESCRIPTION) @RequestParam(required = false) String periodDays) {
        int days = DashboardPeriod.requireValidDays(periodDays);
        String orgId = TenantContextHolder.get();
        return ResponseEntity.ok(mapper.toResponse(getQuality.execute(orgId, projectId, days),
                dataFreshnessResolver.resolve(orgId, projectId)));
    }
}

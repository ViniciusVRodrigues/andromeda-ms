package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.PortfolioRow;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetIntegrationsDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetOrganizationDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.IntegrationsDashboardV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.OrganizationDashboardV2Response;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Organization-wide data endpoints of the v2 dashboards (dashboards-customizaveis D1).
 *
 * <ul>
 *   <li>{@code /organization}: {@code DASHBOARD_STRATEGIC} on at least one project, and only the
 *       projects where it is effective are aggregated ({@link EffectivePermissionService#projectIdsWith},
 *       as level 1 of {@code StrategicDashboardController}); the period is validated after the
 *       authorization (422 {@code DASHBOARD_PERIOD_INVALID}).</li>
 *   <li>{@code /integrations}: {@code AZURE_CONFIGURE}, {@code REPOS_CONFIGURE} or {@code USER_MANAGE}
 *       (organization level); never a secret in the response.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
@Tag(name = "Dashboards v2", description = "Dados reais dos dashboards v2, um payload por visão")
public class OrganizationDashboardsV2Controller {

    private static final String PERIOD_DESCRIPTION = "Dias até hoje: 30, 90 (padrão), 180 ou 365";

    private final GetOrganizationDashboardV2UseCase getOrganization;
    private final GetIntegrationsDashboardV2UseCase getIntegrations;
    private final OrganizationDashboardsV2Mapper mapper;
    private final DataFreshnessResolver dataFreshnessResolver;
    private final CurrentUserPermissions currentUserPermissions;
    private final EffectivePermissionService effectivePermissionService;

    @GetMapping("/organization")
    @PreAuthorize("@perm.onAnyProject('DASHBOARD_STRATEGIC')")
    @Operation(summary = "Visão geral v2 da organização",
            description = "Payload buildOrganization agregando só os projetos em que quem chama tem "
                    + "DASHBOARD_STRATEGIC: indicadores do período e do anterior, portfólio com saúde, entrega, "
                    + "engenharia, IA das pessoas desses projetos e integrações. Ausência de dado é null; durações "
                    + "{p50, p90, n} em horas.")
    @ApiResponse(responseCode = "200", description = "Payload da visão geral da organização")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_STRATEGIC em nenhum projeto")
    @ApiResponse(responseCode = "422", description = "DASHBOARD_PERIOD_INVALID")
    public ResponseEntity<OrganizationDashboardV2Response> organization(
            @Parameter(description = PERIOD_DESCRIPTION) @RequestParam(required = false) String periodDays) {
        int days = DashboardPeriod.requireValidDays(periodDays);
        String orgId = TenantContextHolder.get();
        Set<String> visibleProjectIds = effectivePermissionService.projectIdsWith(
                currentUserPermissions.require(), PermissionCatalog.DASHBOARD_STRATEGIC);
        OrganizationDashboardV2Output out = getOrganization.execute(orgId, visibleProjectIds, days);
        return ResponseEntity.ok(mapper.toResponse(out, dataFreshnessResolver.resolveForProjects(orgId,
                out.portfolio().stream().map(PortfolioRow::projectId).toList())));
    }

    @GetMapping("/integrations")
    @PreAuthorize("hasAnyAuthority('AZURE_CONFIGURE', 'REPOS_CONFIGURE', 'USER_MANAGE')")
    @Operation(summary = "Integrações e identidades v2",
            description = "Payload integrations: estado das conexões, pessoas × vínculos com alertas, execuções de "
                    + "sync dos últimos 14 dias com os últimos problemas, repositórios vinculados e commits sem "
                    + "usuário por login. Nunca inclui PAT, token OAuth ou hash de token do conector.")
    @ApiResponse(responseCode = "200", description = "Payload de integrações")
    @ApiResponse(responseCode = "403", description = "Sem AZURE_CONFIGURE, REPOS_CONFIGURE nem USER_MANAGE")
    public ResponseEntity<IntegrationsDashboardV2Response> integrations() {
        String orgId = TenantContextHolder.get();
        return ResponseEntity.ok(mapper.toResponse(getIntegrations.execute(orgId),
                dataFreshnessResolver.resolveForProjects(orgId, null)));
    }
}

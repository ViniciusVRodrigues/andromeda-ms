package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetIndividualDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.IndividualDashboardV2Response;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.TeamDashboardV2Response;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data endpoints of the v2 dashboards (dashboards-customizaveis D1): one payload per view. The
 * tenant and, for the individual view, the person always come from the JWT — no {@code userId}
 * parameter exists.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
@Tag(name = "Dashboards v2", description = "Dados reais dos dashboards v2, um payload por visão")
public class DashboardsV2Controller {

    private final GetIndividualDashboardV2UseCase getIndividualDashboard;
    private final GetTeamDashboardV2UseCase getTeamDashboard;
    private final DashboardsV2Mapper mapper;
    private final DataFreshnessResolver dataFreshnessResolver;

    @GetMapping("/individual")
    @PreAuthorize("hasAuthority('DASHBOARD_INDIVIDUAL') and @perm.onProject(#projectId, 'DASHBOARD_INDIVIDUAL')")
    @Operation(summary = "Dashboard individual v2",
            description = "Payload buildIndividual da pessoa autenticada no projeto e sprint (sprint atual quando "
                    + "omitida). Ausência de dado é null; durações {p50, p90, n} em horas.")
    @ApiResponse(responseCode = "200", description = "Payload da visão individual")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_INDIVIDUAL no projeto")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização) ou SPRINT_NOT_FOUND (outro projeto)")
    public ResponseEntity<IndividualDashboardV2Response> individual(
            @Parameter(description = "Projeto (obrigatório)") @RequestParam(required = false) String projectId,
            @Parameter(description = "Sprint do projeto; omitida = a atual") @RequestParam(required = false) String sprintId) {
        String orgId = TenantContextHolder.get();
        var output = getIndividualDashboard.execute(orgId, callerId(), projectId, sprintId);
        return ResponseEntity.ok(mapper.toResponse(output, dataFreshnessResolver.resolve(orgId, projectId)));
    }

    @GetMapping("/team")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    @Operation(summary = "Dashboard de equipe v2",
            description = "Payload buildTeam do projeto e sprint (sprint atual quando omitida). O custo de IA é o "
                    + "alocado ao projeto; o não alocado vem à parte. Ausência de dado é null.")
    @ApiResponse(responseCode = "200", description = "Payload da visão de equipe")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_TEAM no projeto")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização) ou SPRINT_NOT_FOUND (outro projeto)")
    public ResponseEntity<TeamDashboardV2Response> team(
            @Parameter(description = "Projeto (obrigatório)") @RequestParam(required = false) String projectId,
            @Parameter(description = "Sprint do projeto; omitida = a atual") @RequestParam(required = false) String sprintId) {
        String orgId = TenantContextHolder.get();
        var output = getTeamDashboard.execute(orgId, callerId(), projectId, sprintId);
        return ResponseEntity.ok(mapper.toResponse(output, dataFreshnessResolver.resolve(orgId, projectId)));
    }

    /** The authenticated user id (the JWT subject); never a request parameter. */
    private static String callerId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

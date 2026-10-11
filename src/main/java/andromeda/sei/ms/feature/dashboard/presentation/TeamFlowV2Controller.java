package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamFlowV2UseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.TeamFlowV2Response;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Team flow of the v2 dashboards (dashboards-customizaveis D1, sub-tab "Fluxo e qualidade"). The
 * tenant comes from the JWT; a missing {@code projectId} is denied by {@code @perm} (403).
 */
@RestController
@RequestMapping("/api/v1/dashboards/team")
@RequiredArgsConstructor
@Tag(name = "Dashboards v2", description = "Dados reais dos dashboards v2, um payload por visão")
public class TeamFlowV2Controller {

    private final GetTeamFlowV2UseCase getTeamFlow;
    private final TeamFlowV2Mapper mapper;
    private final DataFreshnessResolver dataFreshnessResolver;

    @GetMapping("/flow")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    @Operation(summary = "Fluxo da equipe v2",
            description = "Payload buildTeamFlow do projeto e sprint (sprint atual quando omitida), com as 3 categorias "
                    + "de estado gravadas (Proposed/InProgress/Completed) e sem os blocos de coleta nova (quadro por "
                    + "coluna, escopo, horas restantes, trocas de responsável, reviews pedidas, severidade). "
                    + "Ausência de dado é null; durações {p50, p90, n} em horas.")
    @ApiResponse(responseCode = "200", description = "Payload do fluxo da equipe")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_TEAM no projeto (ou sem projectId)")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização) ou SPRINT_NOT_FOUND (outro projeto)")
    public ResponseEntity<TeamFlowV2Response> flow(
            @Parameter(description = "Projeto (obrigatório)") @RequestParam(required = false) String projectId,
            @Parameter(description = "Sprint do projeto; omitida = a atual") @RequestParam(required = false) String sprintId) {
        String orgId = TenantContextHolder.get();
        var output = getTeamFlow.execute(orgId, projectId, sprintId);
        return ResponseEntity.ok(mapper.toResponse(output, dataFreshnessResolver.resolve(orgId, projectId)));
    }
}

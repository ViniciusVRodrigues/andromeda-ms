package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetIndividualDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.ListDashboardPeopleUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.v2.DashboardPersonResponse;
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

import java.util.List;

/**
 * Data endpoints of the v2 dashboards (dashboards-customizaveis D1): one payload per view. The
 * tenant always comes from the JWT. The individual view is the caller's own unless {@code userId}
 * names another project member, which needs {@code DASHBOARD_TEAM} on the project.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
@Tag(name = "Dashboards v2", description = "Dados reais dos dashboards v2, um payload por visão")
public class DashboardsV2Controller {

    private final GetIndividualDashboardV2UseCase getIndividualDashboard;
    private final GetTeamDashboardV2UseCase getTeamDashboard;
    private final ListDashboardPeopleUseCase listPeople;
    private final DashboardsV2Mapper mapper;
    private final DataFreshnessResolver dataFreshnessResolver;

    @GetMapping("/individual")
    @PreAuthorize("(#userId == null or #userId == authentication.name) "
            + "? (hasAuthority('DASHBOARD_INDIVIDUAL') and @perm.onProject(#projectId, 'DASHBOARD_INDIVIDUAL')) "
            + ": @perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    @Operation(summary = "Dashboard individual v2",
            description = "Payload buildIndividual da pessoa autenticada, ou de outro membro do projeto quando "
                    + "userId e DASHBOARD_TEAM no projeto, no projeto e sprint (sprint atual quando omitida). Ausência de dado é null; durações {p50, p90, n} em horas.")
    @ApiResponse(responseCode = "200", description = "Payload da visão individual")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_INDIVIDUAL no projeto (própria) ou sem DASHBOARD_TEAM (outra pessoa)")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização), PROJECT_MEMBER_NOT_FOUND (userId não é membro ativo do projeto) ou SPRINT_NOT_FOUND (outro projeto)")
    public ResponseEntity<IndividualDashboardV2Response> individual(
            @Parameter(description = "Projeto (obrigatório)") @RequestParam(required = false) String projectId,
            @Parameter(description = "Sprint do projeto; omitida = a atual") @RequestParam(required = false) String sprintId,
            @Parameter(description = "Outra pessoa do projeto; omitido = a própria") @RequestParam(required = false) String userId) {
        String orgId = TenantContextHolder.get();
        var output = getIndividualDashboard.execute(orgId, callerId(), userId, projectId, sprintId);
        return ResponseEntity.ok(mapper.toResponse(output, dataFreshnessResolver.resolve(orgId, projectId)));
    }

    @GetMapping("/individual/people")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    @Operation(summary = "Pessoas do projeto para o dashboard individual",
            description = "Membros ativos do projeto (id, nome, cargo), em ordem alfabética, sem e-mail.")
    @ApiResponse(responseCode = "200", description = "Lista de pessoas")
    @ApiResponse(responseCode = "403", description = "Sem DASHBOARD_TEAM no projeto")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND (outra organização)")
    public ResponseEntity<List<DashboardPersonResponse>> individualPeople(
            @Parameter(description = "Projeto (obrigatório)") @RequestParam(required = false) String projectId) {
        var people = listPeople.execute(TenantContextHolder.get(), projectId).stream()
                .map(p -> new DashboardPersonResponse(p.id(), p.name(), p.jobTitle())).toList();
        return ResponseEntity.ok(people);
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

    /** The authenticated user id (the JWT subject). */
    private static String callerId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

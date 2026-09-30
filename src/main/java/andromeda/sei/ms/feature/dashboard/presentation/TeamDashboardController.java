package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.TeamDashboardResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class TeamDashboardController {

    private final GetTeamDashboardUseCase getTeamDashboardUseCase;
    private final TeamDashboardMapper mapper;
    private final DataFreshnessResolver dataFreshnessResolver;

    @GetMapping("/team")
    // No projectId: the use case answers 422 TEAM_DASHBOARD_PROJECT_REQUIRED.
    @PreAuthorize("#projectId == null ? @perm.onAnyProject('DASHBOARD_TEAM') "
            + ": @perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    public ResponseEntity<TeamDashboardResponse> getTeamDashboard(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String orgId = TenantContextHolder.get();
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        var output = getTeamDashboardUseCase.execute(orgId, userId, projectId, sprintId, startDate, endDate);
        return ResponseEntity.ok(mapper.toResponse(output)
                .withDataFreshness(dataFreshnessResolver.resolve(orgId, projectId)));
    }
}

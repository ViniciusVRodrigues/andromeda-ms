package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetDeveloperDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.DeveloperDashboardResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
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
public class DeveloperDashboardController {

    private final GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;
    private final DeveloperDashboardMapper mapper;

    @GetMapping("/developer")
    @PreAuthorize("hasAuthority('DASHBOARD_INDIVIDUAL')")
    public ResponseEntity<DeveloperDashboardResponse> getDeveloperDashboard(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String model) {

        String orgId = TenantContextHolder.get();
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        var output = getDeveloperDashboardUseCase.execute(orgId, userId, projectId, sprintId, startDate, endDate, model);
        return ResponseEntity.ok(mapper.toResponse(output));
    }
}

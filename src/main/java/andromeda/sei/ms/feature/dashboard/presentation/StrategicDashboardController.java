package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetStrategicDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.dto.StrategicDashboardResponse;
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
public class StrategicDashboardController {

    private final GetStrategicDashboardUseCase getStrategicDashboardUseCase;
    private final StrategicDashboardMapper mapper;

    @GetMapping("/strategic")
    @PreAuthorize("hasAuthority('DASHBOARD_STRATEGIC')")
    public ResponseEntity<StrategicDashboardResponse> getStrategicDashboard(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        String orgId = TenantContextHolder.get();
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        var output = getStrategicDashboardUseCase.execute(orgId, userId, projectId, memberId, startDate, endDate);
        return ResponseEntity.ok(mapper.toResponse(output));
    }
}

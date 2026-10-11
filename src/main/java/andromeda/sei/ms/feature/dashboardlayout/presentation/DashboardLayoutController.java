package andromeda.sei.ms.feature.dashboardlayout.presentation;

import andromeda.sei.ms.feature.dashboardlayout.application.usecase.DeleteDashboardLayoutUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.usecase.ListDashboardLayoutsUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.usecase.SaveDashboardLayoutUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.DashboardViewAccess;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardLayoutListResponse;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardLayoutRequest;
import andromeda.sei.ms.feature.dashboardlayout.presentation.dto.DashboardLayoutResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own dashboard layouts (design.md D6). The user and the organization always come
 * from the authenticated principal (JWT), never from the path or the body. Access per view goes
 * through the {@code dashboardLayoutAccess} bean (design.md D3).
 */
@Tag(name = "Dashboard Layouts", description = "Per-user customization of each dashboard view's summary")
@RestController
@RequestMapping("/api/v1/dashboards/layouts")
@RequiredArgsConstructor
public class DashboardLayoutController {

    private static final String VIEW_KEY_DOC = "individual | equipe | projeto | org";

    private final ListDashboardLayoutsUseCase listDashboardLayoutsUseCase;
    private final SaveDashboardLayoutUseCase saveDashboardLayoutUseCase;
    private final DeleteDashboardLayoutUseCase deleteDashboardLayoutUseCase;
    private final DashboardViewAccess dashboardViewAccess;
    private final DashboardLayoutPresentationMapper mapper;

    @Operation(summary = "List the caller's saved layouts",
            description = "Only active layouts of the views the caller may see. A view without a layout uses the catalog default.")
    @ApiResponse(responseCode = "200", description = "Layouts listed")
    @GetMapping
    public ResponseEntity<DashboardLayoutListResponse> list(@AuthenticationPrincipal AndromedaUserDetails principal) {
        var items = listDashboardLayoutsUseCase
                .execute(principal.getOrganizationId(), principal.getUserId(), dashboardViewAccess.accessibleViewKeys())
                .stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(new DashboardLayoutListResponse(items));
    }

    @Operation(summary = "Save the caller's layout of a view",
            description = "version null creates the layout; otherwise it must equal the stored version. Returns the saved layout with version + 1.")
    @ApiResponse(responseCode = "200", description = "Layout saved")
    @ApiResponse(responseCode = "403", description = "No permission for the view")
    @ApiResponse(responseCode = "409", description = "DASHBOARD_LAYOUT_VERSION_CONFLICT: reload and reapply")
    @ApiResponse(responseCode = "422", description = "DASHBOARD_LAYOUT_VIEW_UNKNOWN or DASHBOARD_LAYOUT_INVALID")
    @PutMapping("/{viewKey}")
    @PreAuthorize("@dashboardLayoutAccess.canAccess(#viewKey)")
    public ResponseEntity<DashboardLayoutResponse> save(@AuthenticationPrincipal AndromedaUserDetails principal,
                                                        @Parameter(description = VIEW_KEY_DOC) @PathVariable String viewKey,
                                                        @RequestBody DashboardLayoutRequest request) {
        var input = mapper.toInput(principal.getOrganizationId(), principal.getUserId(), viewKey, request);
        return ResponseEntity.ok(mapper.toResponse(saveDashboardLayoutUseCase.execute(input)));
    }

    @Operation(summary = "Restore the default layout of a view",
            description = "Soft delete of the caller's layout; idempotent.")
    @ApiResponse(responseCode = "204", description = "Default restored (or nothing to restore)")
    @ApiResponse(responseCode = "403", description = "No permission for the view")
    @ApiResponse(responseCode = "422", description = "DASHBOARD_LAYOUT_VIEW_UNKNOWN")
    @DeleteMapping("/{viewKey}")
    @PreAuthorize("@dashboardLayoutAccess.canAccess(#viewKey)")
    public ResponseEntity<Void> restoreDefault(@AuthenticationPrincipal AndromedaUserDetails principal,
                                               @Parameter(description = VIEW_KEY_DOC) @PathVariable String viewKey) {
        deleteDashboardLayoutUseCase.execute(principal.getOrganizationId(), principal.getUserId(), viewKey);
        return ResponseEntity.noContent().build();
    }
}

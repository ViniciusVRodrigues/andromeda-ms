package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.UpdateSyncSettingsInput;
import andromeda.sei.ms.feature.organization.application.usecase.GetSyncSettingsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateSyncSettingsUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.SyncSettingsRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.SyncSettingsResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Organization sync settings", description = "Scheduled sync frequency and first-sync backfill window")
@RestController
@RequestMapping("/api/v1/organizations/me/sync-settings")
@RequiredArgsConstructor
public class OrganizationSyncSettingsController {

    private final GetSyncSettingsUseCase getSyncSettingsUseCase;
    private final UpdateSyncSettingsUseCase updateSyncSettingsUseCase;

    @Operation(summary = "Get the organization's sync settings")
    @ApiResponse(responseCode = "200", description = "Sync settings")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SyncSettingsResponse> get() {
        return ResponseEntity.ok(SyncSettingsResponse.from(getSyncSettingsUseCase.execute(TenantContextHolder.get())));
    }

    @Operation(summary = "Update the organization's sync settings")
    @ApiResponse(responseCode = "200", description = "Sync settings updated")
    @ApiResponse(responseCode = "400", description = "Missing field or unknown frequency")
    @ApiResponse(responseCode = "403", description = "Missing AZURE_CONFIGURE permission")
    @ApiResponse(responseCode = "422", description = "backfillDays outside [7, 730] (ORGANIZATION_BACKFILL_DAYS_INVALID)")
    @PutMapping
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<SyncSettingsResponse> update(@Valid @RequestBody SyncSettingsRequest request) {
        var input = new UpdateSyncSettingsInput(request.scheduledSync(), request.backfillDays());
        return ResponseEntity.ok(SyncSettingsResponse.from(
                updateSyncSettingsUseCase.execute(TenantContextHolder.get(), input)));
    }
}

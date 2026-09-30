package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.UpdateMetricSettingsInput;
import andromeda.sei.ms.feature.organization.application.usecase.GetMetricSettingsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateMetricSettingsUseCase;
import andromeda.sei.ms.feature.organization.presentation.dto.MetricSettingsRequest;
import andromeda.sei.ms.feature.organization.presentation.dto.MetricSettingsResponse;
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

@Tag(name = "Organization metric settings", description = "Remediation rules of the Change Fail Rate proxy")
@RestController
@RequestMapping("/api/v1/organizations/me/metric-settings")
@RequiredArgsConstructor
public class OrganizationMetricSettingsController {

    private final GetMetricSettingsUseCase getMetricSettingsUseCase;
    private final UpdateMetricSettingsUseCase updateMetricSettingsUseCase;

    @Operation(summary = "Get the organization's metric settings")
    @ApiResponse(responseCode = "200", description = "Metric settings (defaults when never configured)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MetricSettingsResponse> get() {
        return ResponseEntity.ok(MetricSettingsResponse.from(
                getMetricSettingsUseCase.execute(TenantContextHolder.get())));
    }

    @Operation(summary = "Update the organization's metric settings")
    @ApiResponse(responseCode = "200", description = "Metric settings updated")
    @ApiResponse(responseCode = "400", description = "Missing field")
    @ApiResponse(responseCode = "403", description = "Missing AZURE_CONFIGURE permission")
    @ApiResponse(responseCode = "422", description = "Blank remediation prefix (METRIC_SETTINGS_INVALID)")
    @PutMapping
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<MetricSettingsResponse> update(@Valid @RequestBody MetricSettingsRequest request) {
        var input = new UpdateMetricSettingsInput(request.remediationBranchPrefixes(),
                request.bugLinkedCountsAsRemediation(), request.revertCountsAsRemediation());
        return ResponseEntity.ok(MetricSettingsResponse.from(
                updateMetricSettingsUseCase.execute(TenantContextHolder.get(), input)));
    }
}

package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.AzureConnectionStatusOutput;
import andromeda.sei.ms.feature.azure.application.usecase.DisconnectAzureUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.GetAzureConnectionStatusUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.TriggerAzureSyncUseCase;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.AzureSyncCooldownService;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureConnectionStatusResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Azure DevOps Integration", description = "Azure DevOps OAuth connection management")
@RestController
@RequestMapping("/api/v1/integrations/azure")
@RequiredArgsConstructor
public class AzureConnectionController {

    private final GetAzureConnectionStatusUseCase getAzureConnectionStatusUseCase;
    private final DisconnectAzureUseCase disconnectAzureUseCase;
    private final TriggerAzureSyncUseCase triggerAzureSyncUseCase;
    private final AzureSyncCooldownService azureSyncCooldownService;

    @Operation(summary = "Initiate Azure DevOps OAuth authorization")
    @ApiResponse(responseCode = "302", description = "Redirect to Microsoft authorization")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/connect")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> connect() {
        return ResponseEntity.status(302)
                .header("Location", "/api/v1/auth/oauth2/authorization/azure")
                .build();
    }

    @Operation(summary = "Get Azure DevOps connection status")
    @ApiResponse(responseCode = "200", description = "Connection status")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/connection")
    @PreAuthorize("hasAuthority('AZURE_READ')")
    public ResponseEntity<AzureConnectionStatusResponse> getStatus() {
        AzureConnectionStatusOutput output = getAzureConnectionStatusUseCase.execute();
        return ResponseEntity.ok(new AzureConnectionStatusResponse(
                output.status(),
                output.azureOrgUrl(),
                output.authorizedByUserId(),
                output.lastSuccessAt(),
                output.consecutiveFailures()
        ));
    }

    @Operation(summary = "Disconnect Azure DevOps integration")
    @ApiResponse(responseCode = "204", description = "Disconnected")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @DeleteMapping("/connection")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> disconnect() {
        disconnectAzureUseCase.execute();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trigger manual Azure DevOps sync")
    @ApiResponse(responseCode = "202", description = "Sync triggered")
    @ApiResponse(responseCode = "409", description = "No connection found")
    @ApiResponse(responseCode = "429", description = "Cooldown active")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> triggerSync() {
        String orgId = TenantContextHolder.get();
        long remaining = azureSyncCooldownService.remainingCooldown(orgId);
        if (remaining > 0) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(remaining))
                    .build();
        }
        try {
            triggerAzureSyncUseCase.execute();
        } catch (AzureConnectionNotFoundException e) {
            return ResponseEntity.status(409).build();
        }
        azureSyncCooldownService.record(orgId);
        return ResponseEntity.accepted().build();
    }
}

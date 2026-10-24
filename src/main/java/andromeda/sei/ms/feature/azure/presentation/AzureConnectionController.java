package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.AzurePatConnectionInput;
import andromeda.sei.ms.feature.azure.application.usecase.ConfigureAzureDevOpsPatUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.DisconnectAzureUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.GetAzureConnectionStatusUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.ListAzureUsersUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.UpdateAzureOrgUrlUseCase;
import andromeda.sei.ms.feature.azure.application.usecase.TriggerAzureSyncUseCase;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectMovedException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureConnectionStatusResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureOrgUrlRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.AzurePatConnectionRequest;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureUserResponse;
import andromeda.sei.ms.feature.azure.presentation.dto.AzureUsersResponse;
import andromeda.sei.ms.shared.presentation.dto.ErrorResponse;
import andromeda.sei.ms.shared.presentation.dto.SyncStartedResponse;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ConfigureAzureDevOpsPatUseCase configureAzureDevOpsPatUseCase;
    private final ListAzureUsersUseCase listAzureUsersUseCase;
    private final UpdateAzureOrgUrlUseCase updateAzureOrgUrlUseCase;

    /** Read again by the OAuth authorization request resolver, which carries it in the state. */

    @Operation(summary = "List all Azure DevOps users across all active project bindings")
    @ApiResponse(responseCode = "200", description = "List of Azure users")
    @ApiResponse(responseCode = "404", description = "Azure connection not found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<AzureUsersResponse> listUsers() {
        List<AzureUserResponse> users = listAzureUsersUseCase.execute().stream()
                .map(u -> new AzureUserResponse(u.azureId(), u.displayName(), u.azureEmail()))
                .toList();
        return ResponseEntity.ok(new AzureUsersResponse(users));
    }

    @Operation(summary = "Moved: Azure DevOps OAuth now starts from a one-time ticket",
            description = "The popup cannot send Authorization. Ask POST /api/v1/auth/oauth2/link-ticket "
                    + "(purpose AZURE_ORG_CONNECT) and open /api/v1/auth/oauth2/authorization/azure?ticket=...")
    @ApiResponse(responseCode = "410", description = "AZURE_CONNECT_MOVED")
    @GetMapping("/connect")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> connect() {
        throw new AzureConnectMovedException();
    }

    @Operation(summary = "Fix the Azure DevOps organization URL without reauthorizing",
            description = "Validates the URL shape and that the stored credential lists the organization's projects; "
                    + "then marks the connection CONNECTED and re-evaluates reposAccess.")
    @ApiResponse(responseCode = "200", description = "Updated connection status")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "422", description = "Malformed or unreachable URL (AZURE_ORG_URL_INVALID)")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PutMapping("/connection/org-url")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<AzureConnectionStatusResponse> updateOrgUrl(@Valid @RequestBody AzureOrgUrlRequest request) {
        return ResponseEntity.ok(AzureConnectionStatusResponse.from(updateAzureOrgUrlUseCase.execute(request.azureOrgUrl())));
    }

    @Operation(summary = "Get Azure DevOps connection status")
    @ApiResponse(responseCode = "200", description = "Connection status")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/connection")
    // Organization-level connection status: open to whoever reads Azure data somewhere.
    @PreAuthorize("@perm.onAnyProject('AZURE_READ')")
    public ResponseEntity<AzureConnectionStatusResponse> getStatus() {
        return ResponseEntity.ok(AzureConnectionStatusResponse.from(getAzureConnectionStatusUseCase.execute()));
    }

    @Operation(summary = "Connect Azure DevOps via Personal Access Token")
    @ApiResponse(responseCode = "204", description = "PAT connection established")
    @ApiResponse(responseCode = "400", description = "Missing fields")
    @ApiResponse(responseCode = "422", description = "Malformed org URL (AZURE_ORG_URL_INVALID) or PAT rejected by "
            + "Azure DevOps (AZURE_PAT_INVALID). A PAT without Code (Read) is accepted with reposAccess=false")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping("/connection/pat")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<Void> connectPat(@Valid @RequestBody AzurePatConnectionRequest request) {
        configureAzureDevOpsPatUseCase.execute(
                new AzurePatConnectionInput(request.adoOrganizationUrl(), request.personalAccessToken()));
        return ResponseEntity.noContent().build();
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

    @Operation(summary = "Trigger manual Azure DevOps sync",
            description = "Asynchronous: answers 202 with a syncId before any external call. Syncs every "
                    + "sync-enabled binding, then recalculates work item, sprint and tokens-per-SP metrics. "
                    + "30s cooldown per binding; a fully successful run recovers a connection in ERROR.")
    @ApiResponse(responseCode = "202", description = "Sync started")
    @ApiResponse(responseCode = "409", description = "No connection found (AZURE_CONNECTION_NOT_FOUND)")
    @ApiResponse(responseCode = "429", description = "Every binding in cooldown or sync queue full; see Retry-After")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('AZURE_CONFIGURE')")
    public ResponseEntity<?> triggerSync() {
        try {
            return ResponseEntity.accepted().body(new SyncStartedResponse(triggerAzureSyncUseCase.execute()));
        } catch (AzureConnectionNotFoundException e) {
            /* Sem conexao o pedido conflita com o estado da integracao (409), nao e um recurso
             * inexistente do path — por isso nao cai no 404 generico do handler global. */
            return ResponseEntity.status(409).body(ErrorResponse.from(e));
        }
    }
}

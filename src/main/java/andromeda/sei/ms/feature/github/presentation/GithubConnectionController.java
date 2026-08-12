package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.AvailableGithubRepoOutput;
import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.application.dto.GithubPatConnectionInput;
import andromeda.sei.ms.feature.github.application.usecase.ConfigureGithubConnectionUseCase;
import andromeda.sei.ms.feature.github.application.usecase.DisconnectGithubUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetGithubConnectionStatusUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ListAvailableGithubReposUseCase;
import andromeda.sei.ms.feature.github.application.usecase.TriggerGithubSyncUseCase;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.infrastructure.GithubSyncCooldownService;
import andromeda.sei.ms.feature.github.presentation.dto.AvailableGithubRepoResponse;
import andromeda.sei.ms.feature.github.presentation.dto.GithubConnectionStatusResponse;
import andromeda.sei.ms.feature.github.presentation.dto.GithubPatConnectionRequest;
import andromeda.sei.ms.feature.github.presentation.dto.GithubRepoBindingResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GitHub Integration", description = "GitHub PAT connection configuration and status")
@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GithubConnectionController {

    private final ConfigureGithubConnectionUseCase configureGithubConnectionUseCase;
    private final GetGithubConnectionStatusUseCase getGithubConnectionStatusUseCase;
    private final DisconnectGithubUseCase disconnectGithubUseCase;
    private final TriggerGithubSyncUseCase triggerGithubSyncUseCase;
    private final GithubSyncCooldownService githubSyncCooldownService;
    private final ListAvailableGithubReposUseCase listAvailableGithubReposUseCase;

    @Operation(summary = "Configure GitHub integration via Personal Access Token")
    @ApiResponse(responseCode = "204", description = "PAT connection established")
    @ApiResponse(responseCode = "400", description = "Invalid or unauthorized PAT")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<Void> configure(@Valid @RequestBody GithubPatConnectionRequest request) {
        configureGithubConnectionUseCase.execute(new GithubPatConnectionInput(request.personalAccessToken()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get GitHub connection status")
    @ApiResponse(responseCode = "200", description = "Connection status")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('GITHUB_READ')")
    public ResponseEntity<GithubConnectionStatusResponse> getStatus() {
        GithubConnectionStatusOutput output = getGithubConnectionStatusUseCase.execute();
        return ResponseEntity.ok(new GithubConnectionStatusResponse(
                output.status(),
                output.authorizedByUserId(),
                output.lastSuccessAt(),
                output.consecutiveFailures(),
                output.bindings().stream()
                        .map(b -> new GithubRepoBindingResponse(b.id(), b.projectId(), b.repoId(), b.syncEnabled(), b.lastSyncAt()))
                        .toList()
        ));
    }

    @Operation(summary = "Disconnect GitHub integration",
            description = "Soft-deactivates the connection and disables sync on all active bindings; "
                    + "synced data is kept (design.md D6)")
    @ApiResponse(responseCode = "204", description = "Disconnected")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @DeleteMapping
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<Void> disconnect() {
        disconnectGithubUseCase.execute();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trigger manual GitHub sync",
            description = "Syncs every sync-enabled active binding with SyncTrigger.MANUAL; 30s "
                    + "per-organization cooldown (design.md D7)")
    @ApiResponse(responseCode = "202", description = "Sync triggered")
    @ApiResponse(responseCode = "409", description = "No connection found")
    @ApiResponse(responseCode = "429", description = "Cooldown active")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<Void> triggerSync() {
        String orgId = TenantContextHolder.get();
        long remaining = githubSyncCooldownService.remainingCooldown(orgId);
        if (remaining > 0) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(remaining))
                    .build();
        }
        try {
            triggerGithubSyncUseCase.execute();
        } catch (GithubConnectionNotFoundException e) {
            return ResponseEntity.status(409).build();
        }
        githubSyncCooldownService.record(orgId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "List GitHub repos available for binding",
            description = "Union of repos visible to the configured PAT and cached repos, excluding "
                    + "repos actively bound to other projects; optional forProjectId keeps the "
                    + "project's own binding for preselection (design.md D5)")
    @ApiResponse(responseCode = "200", description = "Repos listed")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/repos/available")
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<List<AvailableGithubRepoResponse>> listAvailableRepos(
            @RequestParam(required = false) String forProjectId) {
        List<AvailableGithubRepoResponse> repos = listAvailableGithubReposUseCase.execute(forProjectId)
                .stream()
                .map(this::toAvailableRepoResponse)
                .toList();
        return ResponseEntity.ok(repos);
    }

    private AvailableGithubRepoResponse toAvailableRepoResponse(AvailableGithubRepoOutput output) {
        return new AvailableGithubRepoResponse(
                output.repoId(), output.name(), output.fullName(),
                output.source().name(), output.boundToProjectId());
    }
}

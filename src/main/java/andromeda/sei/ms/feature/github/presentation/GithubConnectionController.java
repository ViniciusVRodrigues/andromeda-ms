package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.application.dto.GithubPatConnectionInput;
import andromeda.sei.ms.feature.github.application.usecase.ConfigureGithubConnectionUseCase;
import andromeda.sei.ms.feature.github.application.usecase.DisconnectGithubUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetGithubConnectionStatusUseCase;
import andromeda.sei.ms.feature.github.application.usecase.RepairGithubDataUseCase;
import andromeda.sei.ms.feature.github.application.usecase.TriggerGithubSyncUseCase;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.presentation.dto.AvailableGithubRepoResponse;
import andromeda.sei.ms.feature.github.presentation.dto.GithubConnectionStatusResponse;
import andromeda.sei.ms.feature.github.presentation.dto.GithubPatConnectionRequest;
import andromeda.sei.ms.feature.github.presentation.dto.GithubRepoBindingResponse;
import andromeda.sei.ms.feature.integration.application.usecase.WipeIntegrationDataUseCase;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.ListBindableRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.dto.ErrorResponse;
import andromeda.sei.ms.shared.presentation.dto.SyncStartedResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * GitHub connection (PAT) endpoints — the GitHub-specific part left after vcs-core-refactor.
 * Bindings, repository reads and the work item link settings moved to {@code /api/v1/repos}.
 */
@Tag(name = "GitHub Integration", description = "GitHub PAT connection configuration and status")
@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GithubConnectionController {

    private final ConfigureGithubConnectionUseCase configureGithubConnectionUseCase;
    private final GetGithubConnectionStatusUseCase getGithubConnectionStatusUseCase;
    private final DisconnectGithubUseCase disconnectGithubUseCase;
    private final TriggerGithubSyncUseCase triggerGithubSyncUseCase;
    private final ListBindableRepositoriesUseCase listBindableRepositoriesUseCase;
    private final RepairGithubDataUseCase repairGithubDataUseCase;
    private final WipeIntegrationDataUseCase wipeIntegrationDataUseCase;

    @Operation(summary = "Configure GitHub integration via Personal Access Token")
    @ApiResponse(responseCode = "204", description = "PAT connection established")
    @ApiResponse(responseCode = "400", description = "Invalid or unauthorized PAT")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE")
    @PostMapping
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<Void> configure(@Valid @RequestBody GithubPatConnectionRequest request) {
        configureGithubConnectionUseCase.execute(new GithubPatConnectionInput(request.personalAccessToken()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get GitHub connection status")
    @ApiResponse(responseCode = "200", description = "Connection status")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_READ")
    @GetMapping("/status")
    // Organization-level setting/status: open to whoever reads repository data somewhere.
    @PreAuthorize("@perm.onAnyProject('REPOS_READ')")
    public ResponseEntity<GithubConnectionStatusResponse> getStatus() {
        GithubConnectionStatusOutput output = getGithubConnectionStatusUseCase.execute();
        return ResponseEntity.ok(new GithubConnectionStatusResponse(
                output.status(),
                output.authorizedByUserId(),
                output.lastSuccessAt(),
                output.consecutiveFailures(),
                output.bindings().stream()
                        .map(b -> new GithubRepoBindingResponse(b.id(), b.projectId(), b.repoId(), b.syncEnabled(),
                                b.lastSyncAt()))
                        .toList()));
    }

    @Operation(summary = "Disconnect GitHub integration",
            description = "Soft-deactivates the connection and disables sync on all active GitHub bindings; "
                    + "synced data is kept")
    @ApiResponse(responseCode = "204", description = "Disconnected")
    @ApiResponse(responseCode = "404", description = "No connection found")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE")
    @DeleteMapping
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<Void> disconnect() {
        disconnectGithubUseCase.execute();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Repara os dados do GitHub refazendo um sync completo",
            description = "Zera o watermark de leitura e relê tudo, preservando os registros existentes "
                    + "(upsert). Assíncrono: acompanhe pelo histórico de sincronizações. Passe projectId "
                    + "para limitar a um projeto.")
    @ApiResponse(responseCode = "202", description = "Repair disparado")
    @ApiResponse(responseCode = "403", description = "Sem permissão REPOS_CONFIGURE")
    @PostMapping("/repair")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<SyncStartedResponse> repair(@RequestParam(required = false) String projectId) {
        String syncId = repairGithubDataUseCase.execute(TenantContextHolder.get(), projectId);
        return ResponseEntity.accepted().body(new SyncStartedResponse(syncId));
    }

    /**
     * Hard delete e irreversível. A organização vem do contexto autenticado, nunca do cliente, e o
     * wipe não desconecta a integração — só apaga os dados.
     */
    @Operation(summary = "Apaga permanentemente os dados sincronizados do GitHub",
            description = "Remoção definitiva de commits, pull requests, eventos de review e repositórios do "
                    + "GitHub. Não desconecta a integração nem remove vínculos — um novo sync reconstrói os "
                    + "dados. Passe projectId para limitar a um projeto. Assíncrono.")
    @ApiResponse(responseCode = "202", description = "Wipe disparado")
    @ApiResponse(responseCode = "403", description = "Sem permissão REPOS_CONFIGURE")
    @DeleteMapping("/data")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<Void> wipeData(@RequestParam(required = false) String projectId) {
        var scope = projectId == null
                ? IntegrationDataWiper.WipeScope.wholeIntegration()
                : IntegrationDataWiper.WipeScope.forProject(projectId);
        wipeIntegrationDataUseCase.execute(TenantContextHolder.get(), Integration.GITHUB, scope);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Trigger manual GitHub sync",
            description = "Asynchronous: answers 202 with a syncId before any external call. Syncs every "
                    + "sync-enabled GitHub binding, then recalculates the pull request metrics. 30s cooldown "
                    + "per binding; a fully successful run recovers a connection in ERROR.")
    @ApiResponse(responseCode = "202", description = "Sync started")
    @ApiResponse(responseCode = "409", description = "No connection found (GITHUB_CONNECTION_NOT_FOUND)")
    @ApiResponse(responseCode = "429", description = "Every binding in cooldown or sync queue full; see Retry-After")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE")
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<?> triggerSync() {
        try {
            return ResponseEntity.accepted().body(new SyncStartedResponse(triggerGithubSyncUseCase.execute()));
        } catch (GithubConnectionNotFoundException e) {
            return ResponseEntity.status(409).body(ErrorResponse.from(e));
        }
    }

    /**
     * Deprecated shape of {@code GET /api/v1/repos/available?provider=GITHUB}, kept for one release:
     * {@code repoId} is the {@code owner/name}; repositories bound to other projects are dropped and
     * the one bound to {@code forProjectId} is kept for preselection.
     */
    @Operation(summary = "List GitHub repos available for binding (deprecated)",
            description = "Use GET /api/v1/repos/available?provider=GITHUB")
    @ApiResponse(responseCode = "200", description = "Repos listed")
    @ApiResponse(responseCode = "409", description = "No connection found (VCS_CONNECTION_NOT_FOUND)")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE")
    @GetMapping("/repos/available")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<List<AvailableGithubRepoResponse>> listAvailableRepos(
            @RequestParam(required = false) String forProjectId) {
        return ResponseEntity.ok(listBindableRepositoriesUseCase.execute(VcsProvider.GITHUB).stream()
                .filter(repo -> repo.bindable() || Objects.equals(repo.boundToProjectId(), forProjectId))
                .map(GithubConnectionController::toLegacy)
                .toList());
    }

    private static AvailableGithubRepoResponse toLegacy(BindableRepositoryOutput repo) {
        String name = repo.displayName().contains("/")
                ? repo.displayName().substring(repo.displayName().indexOf('/') + 1) : repo.displayName();
        return new AvailableGithubRepoResponse(repo.displayName(), name, repo.displayName(),
                repo.source().name(), repo.boundToProjectId());
    }
}

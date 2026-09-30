package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.vcs.application.dto.BindRepositoryInput;
import andromeda.sei.ms.feature.vcs.application.dto.BindableRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.BindRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListBindableRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryBindingsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ToggleRepositorySyncUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UnbindRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.presentation.dto.BindRepositoryRequest;
import andromeda.sei.ms.feature.vcs.presentation.dto.ToggleRepositorySyncRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Repository binding management for every provider (vcs-repository-read-api), requiring {@code REPOS_CONFIGURE}. */
@Tag(name = "Repository bindings")
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class VcsRepositoryBindingController {

    private final ListBindableRepositoriesUseCase listBindableRepositoriesUseCase;
    private final BindRepositoryUseCase bindRepositoryUseCase;
    private final ListRepositoryBindingsUseCase listRepositoryBindingsUseCase;
    private final ToggleRepositorySyncUseCase toggleRepositorySyncUseCase;
    private final UnbindRepositoryUseCase unbindRepositoryUseCase;

    @Operation(summary = "Repositories of a provider available to bind",
            description = "Union of what the connection lists now (LIVE) and what earlier syncs stored "
                    + "(CACHED); repositories bound to a project carry boundToProjectId and bindable=false.")
    @ApiResponse(responseCode = "200", description = "Repositories listed")
    @ApiResponse(responseCode = "409", description = "No active connection (VCS_CONNECTION_NOT_FOUND)")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_CONFIGURE")
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<List<BindableRepositoryOutput>> listAvailable(
            @RequestParam(defaultValue = "GITHUB") VcsProvider provider) {
        return ResponseEntity.ok(listBindableRepositoriesUseCase.execute(provider));
    }

    @Operation(summary = "Bind a repository to a project",
            description = "Body {provider, externalRepoId | fullName (GitHub owner/name), syncEnabled}.")
    @ApiResponse(responseCode = "200", description = "Binding created or updated")
    @ApiResponse(responseCode = "409", description = "REPO_ALREADY_BOUND or VCS_CONNECTION_NOT_FOUND")
    @ApiResponse(responseCode = "404", description = "Repository unknown to the provider (REPO_NOT_FOUND)")
    @ApiResponse(responseCode = "422", description = "Malformed identifier (REPO_ID_INVALID)")
    @PostMapping("/projects/{projectId}/bindings")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<RepoBindingOutput> bind(@PathVariable String projectId,
                                                  @Valid @RequestBody BindRepositoryRequest request) {
        return ResponseEntity.ok(bindRepositoryUseCase.execute(projectId, new BindRepositoryInput(
                request.provider(), request.externalRepoId(), request.effectiveFullName(),
                request.effectiveSyncEnabled())));
    }

    @Operation(summary = "List the active repository bindings of the organization")
    @GetMapping("/bindings")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<List<RepoBindingOutput>> listBindings() {
        return ResponseEntity.ok(listRepositoryBindingsUseCase.execute(null));
    }

    @Operation(summary = "List the active repository bindings of a project")
    @GetMapping("/projects/{projectId}/bindings")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<List<RepoBindingOutput>> listProjectBindings(@PathVariable String projectId) {
        return ResponseEntity.ok(listRepositoryBindingsUseCase.execute(projectId));
    }

    @Operation(summary = "Enable or disable sync of a repository binding")
    @ApiResponse(responseCode = "404", description = "REPO_BINDING_NOT_FOUND")
    @PatchMapping("/bindings/{bindingId}")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<RepoBindingOutput> toggleSync(@PathVariable String bindingId,
                                                        @Valid @RequestBody ToggleRepositorySyncRequest request) {
        return ResponseEntity.ok(toggleRepositorySyncUseCase.execute(bindingId, request.syncEnabled()));
    }

    @Operation(summary = "Unbind a repository from a project",
            description = "Soft-deletes the binding and keeps synced data; the repository becomes bindable elsewhere.")
    @ApiResponse(responseCode = "204", description = "Binding removed")
    @ApiResponse(responseCode = "404", description = "REPO_BINDING_NOT_FOUND")
    @DeleteMapping("/projects/{projectId}/bindings/{bindingId}")
    @PreAuthorize("hasAuthority('REPOS_CONFIGURE')")
    public ResponseEntity<Void> unbind(@PathVariable String projectId, @PathVariable String bindingId) {
        unbindRepositoryUseCase.execute(projectId, bindingId);
        return ResponseEntity.noContent().build();
    }
}

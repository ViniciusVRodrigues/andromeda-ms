package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.BindGithubRepoInput;
import andromeda.sei.ms.feature.github.application.dto.ToggleGithubRepoSyncInput;
import andromeda.sei.ms.feature.github.application.usecase.BindGithubRepoUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ToggleGithubRepoSyncUseCase;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.presentation.dto.BindGithubRepoRequest;
import andromeda.sei.ms.feature.github.presentation.dto.GithubRepoBindingResponse;
import andromeda.sei.ms.feature.github.presentation.dto.ToggleGithubRepoSyncRequest;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "GitHub Repo Bindings", description = "GitHub repo-to-project binding management")
@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GithubRepoBindingController {

    private final BindGithubRepoUseCase bindGithubRepoUseCase;
    private final ToggleGithubRepoSyncUseCase toggleGithubRepoSyncUseCase;
    private final GithubRepoBindingRepository githubRepoBindingRepository;

    @Operation(summary = "Bind a GitHub repo to an Andromeda project")
    @ApiResponse(responseCode = "200", description = "Binding created or updated")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PostMapping("/projects/{projectId}/bindings")
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<GithubRepoBindingResponse> bindRepo(
            @PathVariable String projectId,
            @Valid @RequestBody BindGithubRepoRequest request) {
        GithubRepoBinding binding = bindGithubRepoUseCase.execute(projectId,
                new BindGithubRepoInput(request.repoId(), request.syncEnabled()));
        return ResponseEntity.ok(toResponse(binding));
    }

    @Operation(summary = "List all GitHub repo bindings for the organization")
    @ApiResponse(responseCode = "200", description = "Bindings listed")
    @ApiResponse(responseCode = "403", description = "No permission")
    @GetMapping("/bindings")
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<List<GithubRepoBindingResponse>> listBindings() {
        String orgId = TenantContextHolder.get();
        List<GithubRepoBindingResponse> bindings = githubRepoBindingRepository
                .findAllByOrganizationIdAndActiveTrue(orgId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(bindings);
    }

    @Operation(summary = "Enable or disable sync for a repo binding")
    @ApiResponse(responseCode = "200", description = "Sync updated")
    @ApiResponse(responseCode = "404", description = "Binding not found")
    @ApiResponse(responseCode = "403", description = "No permission")
    @PatchMapping("/bindings/{bindingId}")
    @PreAuthorize("hasAuthority('GITHUB_CONFIGURE')")
    public ResponseEntity<GithubRepoBindingResponse> toggleSync(
            @PathVariable String bindingId,
            @RequestBody ToggleGithubRepoSyncRequest request) {
        GithubRepoBinding binding = toggleGithubRepoSyncUseCase.execute(bindingId,
                new ToggleGithubRepoSyncInput(request.syncEnabled()));
        return ResponseEntity.ok(toResponse(binding));
    }

    private GithubRepoBindingResponse toResponse(GithubRepoBinding binding) {
        return new GithubRepoBindingResponse(
                binding.getId(), binding.getProjectId(), binding.getRepoId(),
                binding.isSyncEnabled(), binding.getLastSyncAt());
    }
}

package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.application.dto.GithubPatConnectionInput;
import andromeda.sei.ms.feature.github.application.usecase.ConfigureGithubConnectionUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetGithubConnectionStatusUseCase;
import andromeda.sei.ms.feature.github.presentation.dto.GithubConnectionStatusResponse;
import andromeda.sei.ms.feature.github.presentation.dto.GithubPatConnectionRequest;
import andromeda.sei.ms.feature.github.presentation.dto.GithubRepoBindingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GitHub Integration", description = "GitHub PAT connection configuration and status")
@RestController
@RequestMapping("/api/v1/integrations/github")
@RequiredArgsConstructor
public class GithubConnectionController {

    private final ConfigureGithubConnectionUseCase configureGithubConnectionUseCase;
    private final GetGithubConnectionStatusUseCase getGithubConnectionStatusUseCase;

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
}

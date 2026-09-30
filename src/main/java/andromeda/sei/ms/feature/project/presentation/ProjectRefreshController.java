package andromeda.sei.ms.feature.project.presentation;

import andromeda.sei.ms.feature.project.application.dto.ProjectRefreshStatusOutput;
import andromeda.sei.ms.feature.project.application.usecase.GetProjectRefreshStatusUseCase;
import andromeda.sei.ms.feature.project.application.usecase.RefreshProjectUseCase;
import andromeda.sei.ms.shared.presentation.dto.SyncStartedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project refresh", description = "On-demand, asynchronous refresh of a project's integration data")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/refresh")
@RequiredArgsConstructor
public class ProjectRefreshController {

    private final RefreshProjectUseCase refreshProjectUseCase;
    private final GetProjectRefreshStatusUseCase getProjectRefreshStatusUseCase;

    @Operation(summary = "Start a refresh of every integration binding of the project",
            description = "Asynchronous: answers before any external call. Syncs every active, sync-enabled "
                    + "binding (work tracking and repositories) and recalculates the project's derived metrics.")
    @ApiResponse(responseCode = "202", description = "Refresh started; poll GET .../refresh/{syncId}")
    @ApiResponse(responseCode = "403", description = "Missing AZURE_READ or REPOS_READ on the project")
    @ApiResponse(responseCode = "404", description = "Project not found in this organization (PROJECT_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Project has no binding to refresh (PROJECT_NO_INTEGRATION_BINDING)")
    @ApiResponse(responseCode = "429", description = "Every binding in cooldown or sync queue full; see Retry-After")
    @PostMapping
    @PreAuthorize("@perm.onProject(#projectId, 'AZURE_READ') or @perm.onProject(#projectId, 'REPOS_READ')")
    public ResponseEntity<SyncStartedResponse> refresh(@PathVariable String projectId) {
        String syncId = refreshProjectUseCase.execute(projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new SyncStartedResponse(syncId));
    }

    @Operation(summary = "Get the progress of a project refresh",
            description = "Status RUNNING, SUCCESS, PARTIAL or FAILED, with one entry per binding.")
    @ApiResponse(responseCode = "200", description = "Refresh status")
    @ApiResponse(responseCode = "403", description = "Missing AZURE_READ or REPOS_READ")
    @ApiResponse(responseCode = "404", description = "Refresh not found for this project (PROJECT_REFRESH_NOT_FOUND)")
    @GetMapping("/{syncId}")
    @PreAuthorize("@perm.onProject(#projectId, 'AZURE_READ') or @perm.onProject(#projectId, 'REPOS_READ')")
    public ResponseEntity<ProjectRefreshStatusOutput> status(@PathVariable String projectId,
                                                             @PathVariable String syncId) {
        return ResponseEntity.ok(getProjectRefreshStatusUseCase.execute(projectId, syncId));
    }
}

package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.workitem.application.usecase.ListProjectBacklogOrphansUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.ListProjectBacklogRootsUseCase;
import andromeda.sei.ms.feature.workitem.presentation.dto.WorkItemPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Backlog", description = "Hierarchy-paginated project backlog views")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-items")
@RequiredArgsConstructor
public class ProjectBacklogController {

    private final ListProjectBacklogRootsUseCase listProjectBacklogRootsUseCase;
    private final ListProjectBacklogOrphansUseCase listProjectBacklogOrphansUseCase;

    @Operation(summary = "List top-level (root) work items for a project's backlog, paginated")
    @ApiResponse(responseCode = "200", description = "Roots listed")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @GetMapping("/roots")
    @PreAuthorize("hasAuthority('DASHBOARD_TEAM')")
    public ResponseEntity<WorkItemPageResponse> listRoots(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = listProjectBacklogRootsUseCase.execute(projectId, page, size);
        return ResponseEntity.ok(WorkItemPageResponse.from(result));
    }

    @Operation(summary = "List orphaned work items for a project's backlog, paginated")
    @ApiResponse(responseCode = "200", description = "Orphans listed")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @GetMapping("/orphans")
    @PreAuthorize("hasAuthority('DASHBOARD_TEAM')")
    public ResponseEntity<WorkItemPageResponse> listOrphans(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = listProjectBacklogOrphansUseCase.execute(projectId, page, size);
        return ResponseEntity.ok(WorkItemPageResponse.from(result));
    }
}

package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.workitem.application.usecase.BacklogFilter;
import andromeda.sei.ms.feature.workitem.application.usecase.ListProjectBacklogOrphansUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.ListProjectBacklogRootsUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.feature.workitem.presentation.dto.WorkItemPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

@Tag(name = "Project Backlog", description = "Hierarchy-paginated project backlog views")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/work-items")
@RequiredArgsConstructor
public class ProjectBacklogController {

    private final ListProjectBacklogRootsUseCase listProjectBacklogRootsUseCase;
    private final ListProjectBacklogOrphansUseCase listProjectBacklogOrphansUseCase;
    private final AssigneeNameResolver assigneeNameResolver;
    private final DataFreshnessResolver dataFreshnessResolver;

    @Operation(summary = "List top-level (root) work items for a project's backlog, paginated",
            description = "Without filters, returns only root items for the backlog tree. With any "
                    + "filter (sprintId, assigneeUserId, search, type, status), the hierarchy is "
                    + "flattened and this returns every matching item regardless of classification.")
    @ApiResponse(responseCode = "200", description = "Roots listed")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @GetMapping("/roots")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    public ResponseEntity<WorkItemPageResponse> listRoots(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sprintId,
            @RequestParam(required = false) String assigneeUserId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        var result = listProjectBacklogRootsUseCase.execute(
                projectId, page, size, filterOf(sprintId, assigneeUserId, search, type, status));
        return ResponseEntity.ok(WorkItemPageResponse.from(result, assigneeNames(result)).withLevels(result)
                .withDataFreshness(dataFreshnessResolver.resolve(TenantContextHolder.get(), projectId)));
    }

    @Operation(summary = "List orphaned work items for a project's backlog, paginated",
            description = "Returns empty when any filter is active: the filtered view is a single "
                    + "flat list served by /roots, which already includes orphans.")
    @ApiResponse(responseCode = "200", description = "Orphans listed")
    @ApiResponse(responseCode = "404", description = "Project not found")
    @GetMapping("/orphans")
    @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_TEAM')")
    public ResponseEntity<WorkItemPageResponse> listOrphans(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sprintId,
            @RequestParam(required = false) String assigneeUserId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        var result = listProjectBacklogOrphansUseCase.execute(
                projectId, page, size, filterOf(sprintId, assigneeUserId, search, type, status));
        return ResponseEntity.ok(WorkItemPageResponse.from(result, assigneeNames(result))
                .withDataFreshness(dataFreshnessResolver.resolve(TenantContextHolder.get(), projectId)));
    }

    private BacklogFilter filterOf(String sprintId, String assigneeUserId, String search,
                                    String type, String status) {
        return BacklogFilter.of(sprintId, assigneeUserId, search,
                parseEnum(WorkItem.ResolvedType.class, type),
                parseEnum(WorkItem.ResolvedStatus.class, status));
    }

    /**
     * Um valor desconhecido é tratado como "sem filtro" em vez de 400: o parâmetro é uma
     * conveniência de navegação, e derrubar a listagem inteira porque a UI mandou um tipo que
     * esta versão não conhece seria pior do que ignorá-lo.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Map<String, String> assigneeNames(WorkItemPageResult result) {
        return assigneeNameResolver.resolveFor(result.getContent());
    }
}

package andromeda.sei.ms.feature.workitem.presentation;

import andromeda.sei.ms.feature.workitem.application.dto.UpdateWorkItemManualFieldsInput;
import andromeda.sei.ms.feature.workitem.application.usecase.ListWorkItemChildrenUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.UpdateWorkItemManualFieldsUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.presentation.dto.UpdateWorkItemManualFieldsRequest;
import andromeda.sei.ms.feature.workitem.presentation.dto.WorkItemChildrenResponse;
import andromeda.sei.ms.feature.workitem.presentation.dto.WorkItemManualFieldsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Work Items", description = "Work item manual field overrides")
@RestController
@RequestMapping("/api/v1/work-items")
@RequiredArgsConstructor
public class WorkItemController {

    private final UpdateWorkItemManualFieldsUseCase updateWorkItemManualFieldsUseCase;
    private final ListWorkItemChildrenUseCase listWorkItemChildrenUseCase;
    private final AssigneeNameResolver assigneeNameResolver;

    @Operation(summary = "Manually set or clear complexity/effort on a work item")
    @ApiResponse(responseCode = "200", description = "Work item fields updated")
    @ApiResponse(responseCode = "404", description = "Work item not found")
    @ApiResponse(responseCode = "403", description = "Missing WORK_ITEM_MANAGE permission")
    @PatchMapping("/{id}/manual-fields")
    @PreAuthorize("hasAuthority('WORK_ITEM_MANAGE')")
    public ResponseEntity<WorkItemManualFieldsResponse> updateManualFields(
            @PathVariable String id,
            @Valid @RequestBody UpdateWorkItemManualFieldsRequest request) {
        String callingUserId = SecurityContextHolder.getContext().getAuthentication().getName();

        var input = new UpdateWorkItemManualFieldsInput(
                request.complexity() != null || request.clearComplexityOverrideOrFalse(),
                request.complexity(),
                request.clearComplexityOverrideOrFalse(),
                request.effort() != null || request.clearEffortOverrideOrFalse(),
                request.effort(),
                request.clearEffortOverrideOrFalse());

        WorkItem updated = updateWorkItemManualFieldsUseCase.execute(id, callingUserId, input);

        return ResponseEntity.ok(new WorkItemManualFieldsResponse(
                updated.getId(),
                updated.getComplexity(),
                updated.getComplexitySource() != null ? updated.getComplexitySource().name() : null,
                updated.getEffort(),
                updated.getEffortSource() != null ? updated.getEffortSource().name() : null));
    }

    @Operation(summary = "List the children of a work item, capped at a soft limit")
    @ApiResponse(responseCode = "200", description = "Children listed")
    @ApiResponse(responseCode = "404", description = "Work item not found")
    @GetMapping("/{id}/children")
    @PreAuthorize("hasAuthority('DASHBOARD_TEAM')")
    public ResponseEntity<WorkItemChildrenResponse> listChildren(@PathVariable String id) {
        var output = listWorkItemChildrenUseCase.execute(id);
        return ResponseEntity.ok(WorkItemChildrenResponse.from(
                output, assigneeNameResolver.resolveFor(output.children())));
    }
}

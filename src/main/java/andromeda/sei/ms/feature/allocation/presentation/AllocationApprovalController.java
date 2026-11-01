package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.usecase.DecideAllocationApprovalUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetApprovalQueueUseCase;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotAwaitingApprovalException;
import andromeda.sei.ms.feature.allocation.presentation.dto.ApprovalQueueItemResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.ApproveAllocationsRequest;
import andromeda.sei.ms.feature.allocation.presentation.dto.ApproveAllocationsResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.ReturnAllocationRequest;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Approval queue of the tech lead (uso-ia-fora-de-entrega, design 2.3). The allocation is not a project, so
 * the fine check (AI_ALLOCATION_APPROVE on one of the allocation approver projects, never the own day)
 * lives in the use cases; the gate here only asks for the permission on some project.
 */
@Tag(name = "AI Allocation Approvals", description = "Approval queue for AI usage that needs a tech lead")
@RestController
@RequestMapping("/api/v1/ai/allocations")
@RequiredArgsConstructor
public class AllocationApprovalController {

    static final String APPROVER = "@perm.onAnyProject('AI_ALLOCATION_APPROVE')";

    private final GetApprovalQueueUseCase getApprovalQueue;
    private final DecideAllocationApprovalUseCase decideApproval;
    private final CurrentUserPermissions currentUserPermissions;

    @Operation(summary = "Approval queue", description = "Days awaiting the approval of the caller (oldest request first), never the own day of the caller")
    @ApiResponse(responseCode = "200", description = "Queue returned")
    @GetMapping("/approvals")
    @PreAuthorize(APPROVER)
    public ResponseEntity<List<ApprovalQueueItemResponse>> queue() {
        return ResponseEntity.ok(getApprovalQueue.execute(currentUserPermissions.require()).stream()
                .map(ApprovalQueueItemResponse::from).toList());
    }

    @Operation(summary = "Approve one day", description = "AWAITING_APPROVAL becomes VALIDATED")
    @ApiResponse(responseCode = "204", description = "Approved")
    @ApiResponse(responseCode = "403", description = "Not an approver (ALLOCATION_APPROVAL_FORBIDDEN) or own day (ALLOCATION_APPROVAL_SELF)")
    @ApiResponse(responseCode = "404", description = "Allocation not found")
    @ApiResponse(responseCode = "409", description = "Not awaiting approval, or changed meanwhile")
    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVER)
    public ResponseEntity<Void> approve(@PathVariable String id) {
        decideApproval.approve(currentUserPermissions.require(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Approve several days", description = "1 to 50 ids; what cannot be approved comes back in skipped with the reason")
    @ApiResponse(responseCode = "200", description = "Approved ids and skipped ones")
    @ApiResponse(responseCode = "400", description = "Ids missing, blank or more than 50")
    @PostMapping("/approvals/approve")
    @PreAuthorize(APPROVER)
    public ResponseEntity<ApproveAllocationsResponse> approveBatch(@RequestBody @Valid ApproveAllocationsRequest request) {
        DecideAllocationApprovalUseCase.BatchResult result =
                decideApproval.approveBatch(currentUserPermissions.require(), request.ids());
        return ResponseEntity.ok(new ApproveAllocationsResponse(result.approved(),
                result.skipped().stream()
                        .map(s -> new ApproveAllocationsResponse.Skipped(s.id(), s.reason().name(), s.decidedByName()))
                        .toList()));
    }

    @Operation(summary = "Return one day to its owner", description = "AWAITING_APPROVAL goes back to PENDING_VALIDATION with the sentence of the approver")
    @ApiResponse(responseCode = "204", description = "Returned")
    @ApiResponse(responseCode = "400", description = "Note blank or longer than 280")
    @ApiResponse(responseCode = "403", description = "Not an approver or own day")
    @ApiResponse(responseCode = "404", description = "Allocation not found")
    @ApiResponse(responseCode = "409", description = "Not awaiting approval, or changed meanwhile")
    @PostMapping("/{id}/return")
    @PreAuthorize(APPROVER)
    public ResponseEntity<Void> returnToOwner(@PathVariable String id, @RequestBody @Valid ReturnAllocationRequest request) {
        decideApproval.returnToOwner(currentUserPermissions.require(), id, request.note());
        return ResponseEntity.noContent().build();
    }

    /** 409 with {@code decidedByName}, so the screen can say who got there first. */
    @ExceptionHandler(AllocationNotAwaitingApprovalException.class)
    public ResponseEntity<Map<String, Object>> handleNotAwaiting(AllocationNotAwaitingApprovalException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("decidedByName", ex.getDecidedByName());
        body.put("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}

package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryInput;
import andromeda.sei.ms.feature.allocation.application.usecase.AcceptAllocationUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationCandidatesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationHistoryUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationBatchesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationsUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.ManualAllocateUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.SaveAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.presentation.dto.AllocationEntryRequest;
import andromeda.sei.ms.feature.allocation.presentation.dto.AllocationEntryResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.AllocationHistoryResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.ManualAllocationForDayRequest;
import andromeda.sei.ms.feature.allocation.presentation.dto.ManualAllocationRequest;
import andromeda.sei.ms.feature.allocation.presentation.dto.PendingAllocationDayResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.PendingBatchResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.SplitResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.WorkItemCandidateResponse;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "AI Usage Allocations", description = "Allocation management endpoints for AI usage records")
@RestController
@RequestMapping("/api/v1/ai/allocations")
@RequiredArgsConstructor
public class AiUsageAllocationController {

    private final GetPendingAllocationsUseCase getPendingAllocations;
    private final GetAllocationCandidatesUseCase getAllocationCandidates;
    private final SaveAllocationUseCase saveAllocation;
    private final GetPendingAllocationBatchesUseCase getPendingAllocationBatches;
    private final AcceptAllocationUseCase acceptAllocation;
    private final ManualAllocateUseCase manualAllocate;
    private final GetAllocationHistoryUseCase getAllocationHistory;
    private final CurrentUserPermissions currentUserPermissions;

    /** Own allocations (AI_READ_OWN) or, with AI_READ_ALL on some project, those of its members. */
    static final String AI_READ = "hasAuthority('AI_READ_OWN') or @perm.onAnyProject('AI_READ_ALL')";

    @Operation(summary = "Get pending allocations (legacy)", description = "Returns pending allocation days for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Pending allocations returned")
    @GetMapping("/pending")
    @PreAuthorize(AI_READ)
    public ResponseEntity<List<PendingAllocationDayResponse>> getPending(
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<PendingAllocationDayResponse> response = getPendingAllocations
                .execute(orgId, user.getUserId())
                .stream()
                .map(d -> new PendingAllocationDayResponse(
                        d.day(),
                        d.status(),
                        d.allocations().stream()
                                .map(e -> new AllocationEntryResponse(
                                        e.workItemId(), e.workItemTitle(),
                                        e.percentage(), e.assignedBy()))
                                .toList()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get pending allocation batches", description = "Returns unallocated usage records grouped by day with auto-suggestions")
    @ApiResponse(responseCode = "200", description = "Pending batches returned")
    @GetMapping("/pending-batches")
    @PreAuthorize(AI_READ)
    public ResponseEntity<List<PendingBatchResponse>> getPendingBatches(
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        String effectiveUserId = resolveUserId(userId, user.getUserId());

        List<PendingBatchResponse> response = getPendingAllocationBatches
                .execute(orgId, effectiveUserId)
                .stream()
                .map(b -> new PendingBatchResponse(
                        b.day(), b.totalInputTokens(), b.totalOutputTokens(),
                        b.totalInteractions(), b.estimatedCostUsd(),
                        b.suggestionAllocationId(),
                        b.suggestion().stream()
                                .map(s -> new SplitResponse(s.workItemId(), s.workItemTitle(), s.percentage()))
                                .toList()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Accept auto-suggested allocation", description = "Accepts a PENDING_VALIDATION allocation, setting it to VALIDATED")
    @ApiResponse(responseCode = "204", description = "Allocation accepted")
    @ApiResponse(responseCode = "404", description = "Allocation not found")
    @ApiResponse(responseCode = "409", description = "Allocation already validated")
    @ApiResponse(responseCode = "403", description = "Not the owner of this allocation")
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<Void> accept(
            @PathVariable String id,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        acceptAllocation.execute(orgId, user.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Manually allocate an existing suggestion", description = "Replaces splits on a PENDING_VALIDATION allocation and validates it")
    @ApiResponse(responseCode = "204", description = "Allocation saved")
    @ApiResponse(responseCode = "404", description = "Allocation not found")
    @ApiResponse(responseCode = "409", description = "Allocation already validated")
    @ApiResponse(responseCode = "422", description = "Splits do not sum to 1.0")
    @PostMapping("/{id}/manual")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<Void> manualAllocateById(
            @PathVariable String id,
            @RequestBody @Valid ManualAllocationRequest request,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<AllocationSplit> splits = mapSplits(request.splits());
        manualAllocate.execute(orgId, user.getUserId(), id, null, splits);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Manually allocate for a day without a suggestion", description = "Creates a new VALIDATED allocation for unallocated records on the given day")
    @ApiResponse(responseCode = "204", description = "Allocation created")
    @ApiResponse(responseCode = "422", description = "Splits do not sum to 1.0")
    @PostMapping("/manual")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<Void> manualAllocateForDay(
            @RequestBody @Valid ManualAllocationForDayRequest request,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<AllocationSplit> splits = mapSplits(request.splits());
        manualAllocate.execute(orgId, user.getUserId(), null, request.day(), splits);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get allocation history", description = "Returns paginated validated allocations, optionally filtered by month")
    @ApiResponse(responseCode = "200", description = "History returned")
    @GetMapping("/history")
    @PreAuthorize(AI_READ)
    public ResponseEntity<Page<AllocationHistoryResponse>> getHistory(
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        String effectiveUserId = resolveUserId(userId, user.getUserId());
        YearMonth yearMonth = (month != null) ? YearMonth.parse(month) : null;

        Page<AllocationHistoryResponse> response = getAllocationHistory
                .execute(orgId, effectiveUserId, yearMonth, PageRequest.of(page, size))
                .map(h -> new AllocationHistoryResponse(
                        h.id(), h.day(),
                        h.splits().stream()
                                .map(s -> new SplitResponse(s.workItemId(), s.workItemTitle(), s.percentage()))
                                .toList(),
                        h.assignedBy(), h.updatedAt()));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get candidates for a day (legacy)", description = "Returns work item candidates for allocation on a given day")
    @ApiResponse(responseCode = "200", description = "Candidates returned")
    @GetMapping("/{day}/candidates")
    @PreAuthorize(AI_READ)
    public ResponseEntity<List<WorkItemCandidateResponse>> getCandidates(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<WorkItemCandidateResponse> response = getAllocationCandidates
                .execute(orgId, user.getUserId(), day)
                .stream()
                .map(c -> new WorkItemCandidateResponse(
                        c.workItemId(), c.title(), c.sprintName(), c.type(), c.externalId(), c.storyPoints(),
                        c.complexity(), c.effort()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Save allocation for a day (legacy)", description = "Saves or replaces allocation entries for a given day")
    @ApiResponse(responseCode = "204", description = "Allocation saved")
    @PutMapping("/{day}")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<Void> save(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestBody List<AllocationEntryRequest> body,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<AllocationEntryInput> inputs = body.stream()
                .map(r -> new AllocationEntryInput(r.workItemId(), r.workItemTitle(), r.percentage()))
                .toList();
        saveAllocation.execute(orgId, user.getUserId(), day, inputs);
        return ResponseEntity.noContent().build();
    }

    private String resolveUserId(String requestedUserId, String callerUserId) {
        if (requestedUserId == null || requestedUserId.equals(callerUserId)) {
            return callerUserId;
        }
        // Only members of projects where the caller holds AI_READ_ALL/DASHBOARD_TEAM (6.4).
        if (!currentUserPermissions.canReadUserData(requestedUserId)) {
            throw new ForbiddenOperationException("ALLOCATION_FORBIDDEN",
                    "You are not authorised to view another user's allocations");
        }
        return requestedUserId;
    }

    private List<AllocationSplit> mapSplits(
            List<andromeda.sei.ms.feature.allocation.presentation.dto.AllocationSplitRequest> splitRequests) {
        return splitRequests.stream()
                .map(r -> new AllocationSplit(r.workItemId(), r.workItemTitle(), r.percentage()))
                .toList();
    }
}

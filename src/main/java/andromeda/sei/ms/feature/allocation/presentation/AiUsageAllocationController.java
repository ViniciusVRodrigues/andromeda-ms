package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryInput;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationCandidatesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationsUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.SaveAllocationUseCase;
import andromeda.sei.ms.feature.allocation.presentation.dto.AllocationEntryRequest;
import andromeda.sei.ms.feature.allocation.presentation.dto.AllocationEntryResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.PendingAllocationDayResponse;
import andromeda.sei.ms.feature.allocation.presentation.dto.WorkItemCandidateResponse;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai-usage/allocations")
@RequiredArgsConstructor
public class AiUsageAllocationController {

    private final GetPendingAllocationsUseCase getPendingAllocations;
    private final GetAllocationCandidatesUseCase getAllocationCandidates;
    private final SaveAllocationUseCase saveAllocation;

    @GetMapping
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
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

    @GetMapping("/{day}/candidates")
    @PreAuthorize("hasAuthority('AI_READ_OWN')")
    public ResponseEntity<List<WorkItemCandidateResponse>> getCandidates(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @AuthenticationPrincipal AndromedaUserDetails user) {
        String orgId = TenantContextHolder.get();
        List<WorkItemCandidateResponse> response = getAllocationCandidates
                .execute(orgId, user.getUserId(), day)
                .stream()
                .map(c -> new WorkItemCandidateResponse(c.workItemId(), c.title(), c.sprintName()))
                .toList();
        return ResponseEntity.ok(response);
    }

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
}

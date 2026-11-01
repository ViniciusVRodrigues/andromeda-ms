package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.AllocationApproverAccess;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationApprovalForbiddenException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationApprovalSelfException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotAwaitingApprovalException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationReturnNoteInvalidException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationVersionConflictException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The approver's decision on an {@code AWAITING_APPROVAL} day (uso-ia-fora-de-entrega, design 2.3): approve
 * (alone or in a batch) or return to the owner with a sentence. Nobody decides their own day.
 */
@Service
@RequiredArgsConstructor
public class DecideAllocationApprovalUseCase {

    public static final int NOTE_MAX = 280;
    public static final int BATCH_MAX = 50;

    public enum SkipReason { FORBIDDEN, SELF, NOT_AWAITING, CONFLICT, NOT_FOUND }

    public record Skipped(String id, SkipReason reason, String decidedByName) {}

    public record BatchResult(List<String> approved, List<Skipped> skipped) {}

    private final AiUsageAllocationRepository allocationRepository;
    private final AllocationApproverAccess approverAccess;
    private final UserRepository userRepository;
    private final WorkItemRepository workItemRepository;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;

    public void approve(UserPermissionSnapshot caller, String allocationId) {
        approveOne(caller, approverAccess.approverProjects(caller), allocationId);
    }

    /** Approves each distinct id; what cannot be approved is skipped with the reason, the rest goes on. */
    public BatchResult approveBatch(UserPermissionSnapshot caller, List<String> ids) {
        Set<String> projects = approverAccess.approverProjects(caller);
        List<String> approved = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids)) {
            try {
                approveOne(caller, projects, id);
                approved.add(id);
            } catch (AllocationNotFoundException e) {
                skipped.add(new Skipped(id, SkipReason.NOT_FOUND, null));
            } catch (AllocationApprovalSelfException e) {
                skipped.add(new Skipped(id, SkipReason.SELF, null));
            } catch (AllocationApprovalForbiddenException e) {
                skipped.add(new Skipped(id, SkipReason.FORBIDDEN, null));
            } catch (AllocationNotAwaitingApprovalException e) {
                skipped.add(new Skipped(id, SkipReason.NOT_AWAITING, e.getDecidedByName()));
            } catch (AllocationVersionConflictException e) {
                skipped.add(new Skipped(id, SkipReason.CONFLICT, null));
            }
        }
        return new BatchResult(approved, skipped);
    }

    /** Back to {@code PENDING_VALIDATION} with the same splits as suggestion, the sentence and who decided. */
    public void returnToOwner(UserPermissionSnapshot caller, String allocationId, String note) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.isEmpty() || trimmed.length() > NOTE_MAX) throw new AllocationReturnNoteInvalidException();

        AiUsageAllocation allocation = loadDecidable(caller, approverAccess.approverProjects(caller), allocationId);
        Instant now = Instant.now();
        AiUsageAllocation.Approval previous = allocation.getApproval();
        allocation.setStatus(AiUsageAllocation.Status.PENDING_VALIDATION);
        allocation.setApproval(new AiUsageAllocation.Approval(previous.reasons(), previous.approverProjectIds(),
                previous.requestedAt(), AiUsageAllocation.ApprovalDecision.RETURNED, caller.userId(), now, trimmed));
        List<AiUsageAllocation.ReviewReason> reasons = new ArrayList<>(allocation.getReviewReasons().stream()
                .filter(r -> r.code() != AiUsageAllocation.ReviewCode.RETURNED).toList());
        reasons.add(new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.RETURNED, null, null, null, trimmed));
        allocation.setReviewReasons(reasons);
        allocation.setReviewAcknowledgedAt(null);
        allocation.setUpdatedAt(now);
        save(allocation);
    }

    private void approveOne(UserPermissionSnapshot caller, Set<String> callerProjects, String allocationId) {
        AiUsageAllocation allocation = loadDecidable(caller, callerProjects, allocationId);
        Instant now = Instant.now();
        AiUsageAllocation.Approval previous = allocation.getApproval();
        allocation.setStatus(AiUsageAllocation.Status.VALIDATED);
        allocation.setApproval(new AiUsageAllocation.Approval(previous.reasons(), previous.approverProjectIds(),
                previous.requestedAt(), AiUsageAllocation.ApprovalDecision.APPROVED, caller.userId(), now, null));
        allocation.setUpdatedAt(now);
        save(allocation);

        // Same stamp and ratios the confirm/accept paths apply when a day becomes VALIDATED.
        allocationRepository.updateAllocationIdOnUsageRecords(allocation.getAiUsageIds(), allocation.getId());
        projectIdsOf(caller.organizationId(), allocation)
                .forEach(projectId -> calculateAiRatiosUseCase.execute(caller.organizationId(), projectId));
    }

    /** 404 (also another organization), then own day, then not an approver, then not awaiting. */
    private AiUsageAllocation loadDecidable(UserPermissionSnapshot caller, Set<String> callerProjects, String allocationId) {
        AiUsageAllocation allocation = allocationRepository.findById(allocationId, caller.organizationId())
                .orElseThrow(() -> new AllocationNotFoundException(allocationId));
        if (caller.userId().equals(allocation.getUserId())) throw new AllocationApprovalSelfException(allocationId);
        if (!approverAccess.canDecide(caller, callerProjects, allocation)) {
            throw new AllocationApprovalForbiddenException(allocationId);
        }
        if (allocation.getStatus() != AiUsageAllocation.Status.AWAITING_APPROVAL || allocation.getApproval() == null) {
            throw new AllocationNotAwaitingApprovalException(allocationId,
                    decidedByName(caller.organizationId(), allocation));
        }
        return allocation;
    }

    private void save(AiUsageAllocation allocation) {
        try {
            allocationRepository.save(allocation);
        } catch (OptimisticLockingFailureException e) {
            throw new AllocationVersionConflictException(allocation.getId());
        }
    }

    private String decidedByName(String organizationId, AiUsageAllocation allocation) {
        if (allocation.getApproval() == null || allocation.getApproval().decidedBy() == null) return null;
        return userRepository.findByIdAndOrganizationId(allocation.getApproval().decidedBy(), organizationId)
                .map(User::getName).orElse(null);
    }

    private Set<String> projectIdsOf(String organizationId, AiUsageAllocation allocation) {
        if (allocation.getSplits() == null) return Set.of();
        Set<String> projectIds = new LinkedHashSet<>();
        for (AllocationSplit s : allocation.getSplits()) {
            if (s.getWorkItemId() == null || s.getWorkItemId().isBlank()) continue;
            workItemRepository.findByIdAndOrganizationIdAndActiveTrue(s.getWorkItemId(), organizationId)
                    .map(WorkItem::getProjectId).filter(Objects::nonNull).ifPresent(projectIds::add);
        }
        return projectIds;
    }
}

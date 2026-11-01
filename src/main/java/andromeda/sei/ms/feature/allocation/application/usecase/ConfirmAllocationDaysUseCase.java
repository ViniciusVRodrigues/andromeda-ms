package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalService;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The person confirms whole days (atribuicao-a-prova-de-esquecimento, 1.3): every pending allocation of
 * the day (agent's and automatic) becomes {@code VALIDATED} keeping {@code assignedBy}. A day with review
 * reasons is only confirmed on purpose ({@code includeReview}); ratios are recalculated once per day.
 */
@Service
@RequiredArgsConstructor
public class ConfirmAllocationDaysUseCase {

    public enum SkipReason { NEEDS_REVIEW, NOTHING_PENDING, CONFLICT }

    public record Skipped(LocalDate day, SkipReason reason) {
    }

    /** {@code awaitingApproval}: days whose confirmation fell in the approval policy (they are not in {@code confirmed}). */
    public record Result(List<LocalDate> confirmed, List<Skipped> skipped, List<LocalDate> awaitingApproval) {
        public Result(List<LocalDate> confirmed, List<Skipped> skipped) {
            this(confirmed, skipped, List.of());
        }
    }

    private final AiUsageAllocationRepository allocationRepository;
    private final WorkItemRepository workItemRepository;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;
    private final AllocationApprovalService approvalService;

    public Result execute(String organizationId, String userId, List<LocalDate> days, boolean includeReview) {
        List<LocalDate> confirmed = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<LocalDate> awaiting = new ArrayList<>();
        for (LocalDate day : days) {
            List<AiUsageAllocation> pending = allocationRepository.findByOrgUserAndDay(organizationId, userId, day)
                    .stream()
                    .filter(AiUsageAllocation::isActive)
                    .filter(a -> a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION)
                    .filter(a -> userId.equals(a.getUserId()))
                    .toList();
            if (pending.isEmpty()) {
                skipped.add(new Skipped(day, SkipReason.NOTHING_PENDING));
                continue;
            }
            if (!includeReview && pending.stream().anyMatch(AiUsageAllocation::hasReviewReasons)) {
                skipped.add(new Skipped(day, SkipReason.NEEDS_REVIEW));
                continue;
            }
            try {
                if (confirmDay(organizationId, userId, day, pending)) {
                    awaiting.add(day);
                } else {
                    confirmed.add(day);
                }
            } catch (OptimisticLockingFailureException e) {
                skipped.add(new Skipped(day, SkipReason.CONFLICT));
            }
        }
        return new Result(confirmed, skipped, awaiting);
    }

    /**
     * Validates each pending allocation of the day, or sends it to approval when the policy asks for it.
     * Each one is saved before the next is evaluated, so the month budget sees the earlier ones.
     *
     * @return whether any allocation of the day went to approval
     */
    private boolean confirmDay(String organizationId, String userId, LocalDate day, List<AiUsageAllocation> pending) {
        Instant now = Instant.now();
        Set<String> projectIds = new LinkedHashSet<>();
        boolean anyAwaiting = false;
        for (AiUsageAllocation allocation : pending) {
            AllocationApprovalPolicy.Decision decision = approvalService.evaluate(organizationId, userId, day,
                    allocation.getAiUsageIds(), allocation.getSplits() == null ? List.of() : allocation.getSplits(),
                    List.of());
            decision.applyTo(allocation, now);
            if (allocation.hasReviewReasons()) allocation.setReviewAcknowledgedAt(now);
            allocation.setUpdatedAt(now);
            AiUsageAllocation saved = allocationRepository.save(allocation);
            allocationRepository.updateAllocationIdOnUsageRecords(saved.getAiUsageIds(), saved.getId());
            if (saved.getStatus() == AiUsageAllocation.Status.VALIDATED) {
                projectIds.addAll(projectIdsOf(organizationId, saved));
            } else {
                anyAwaiting = true;
            }
        }
        projectIds.forEach(projectId -> calculateAiRatiosUseCase.execute(organizationId, projectId));
        return anyAwaiting;
    }

    private Set<String> projectIdsOf(String organizationId, AiUsageAllocation allocation) {
        Set<String> projects = new LinkedHashSet<>();
        if (allocation.getSplits() == null) return projects;
        for (AllocationSplit split : allocation.getSplits()) {
            String id = split.getWorkItemId();
            if (id == null || id.isBlank()) continue;
            workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                    .map(WorkItem::getProjectId).filter(Objects::nonNull).ifPresent(projects::add);
        }
        return projects;
    }
}

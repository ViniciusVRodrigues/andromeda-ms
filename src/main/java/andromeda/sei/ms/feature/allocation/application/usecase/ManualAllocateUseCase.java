package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.AgentReviewReasonCalculator;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalService;
import andromeda.sei.ms.feature.allocation.application.AllocationSplitsValidator;
import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNothingToAllocateException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationWorkItemTypeNotEligibleException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ManualAllocateUseCase {

    /** Value of {@code attributionSource} stamped on agent rows the person reallocated by hand. */
    static final String MANUAL_SOURCE = "MANUAL";

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final WorkItemRepository workItemRepository;
    private final AllocationTypeEligibility typeEligibility;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;
    private final AgentUsageQueries agentUsageQueries;
    private final AllocationApprovalService approvalService;

    /** {@code approval} is null when the allocation went straight to {@code VALIDATED}. */
    public record Result(String allocationId, AiUsageAllocation.Status status, AiUsageAllocation.Approval approval) {
    }

    /** What a manual allocation of a day replaces: the person's unconfirmed allocations plus rows with none. */
    record DayRows(List<AiUsageAllocation> replaced, Set<String> agentRowIds, Set<String> usageIds) {
    }

    public Result execute(String organizationId, String userId, String allocationId,
                        LocalDate day, List<AllocationSplit> rawSplits) {
        List<AllocationSplit> splits = AllocationSplitsValidator.normalize(rawSplits);
        AllocationSplitsValidator.validate(splits);

        if (allocationId != null) {
            AiUsageAllocation allocation = allocationRepository.findById(allocationId, organizationId)
                    .orElseThrow(() -> new AllocationNotFoundException(allocationId));
            if (!allocation.getUserId().equals(userId)) {
                throw new ForbiddenOperationException("ALLOCATION_FORBIDDEN",
                        "You are not authorised to modify this allocation");
            }
            if (allocation.getStatus() == AiUsageAllocation.Status.VALIDATED) {
                throw new AllocationAlreadyValidatedException(allocationId);
            }
            return absorbDay(organizationId, userId, allocation.getDay(), splits, false);
        } else {
            List<AiUsageAllocation> ofTheDay = allocationRepository.findByOrgUserAndDay(organizationId, userId, day);
            // A closed day still takes what arrived after it closed (rows with no allocation, an agent's
            // pending allocation): only a day with nothing left to absorb answers 409. The VALIDATED
            // allocations are never touched; absorbDay leaves them as they are.
            boolean dayClosed = ofTheDay.stream().anyMatch(a -> a.isActive() && a.closesTheDay());
            return absorbDay(organizationId, userId, day, splits, dayClosed);
        }
    }

    /**
     * The person's split replaces everything not yet confirmed on the day (atribuicao-a-prova-de-esquecimento,
     * 1.4): every pending allocation (agent's and automatic) plus the rows with no allocation go to one
     * {@code VALIDATED MANUAL} allocation. The pending ones are dropped (automatic) or deactivated (agent's),
     * and the rows that came from the agent are stamped {@code MANUAL}, so the manual correction wins over a
     * resend. {@code VALIDATED} allocations stay as they are.
     */
    private Result absorbDay(String organizationId, String userId, LocalDate day, List<AllocationSplit> splits,
                             boolean dayClosed) {
        DayRows dayRows = collectDayRows(allocationRepository, aiUsageRepository, organizationId, userId, day);
        List<AiUsageAllocation> pending = dayRows.replaced();
        Set<String> agentRowIds = dayRows.agentRowIds();
        Set<String> usageIds = dayRows.usageIds();
        Map<String, WorkItem> items = requireOrganizationWorkItems(organizationId, splits);
        if (usageIds.isEmpty()) {
            // Nothing left to absorb on a closed day is a stale screen or a double click: 409, nothing written.
            if (dayClosed) throw new AllocationAlreadyValidatedException(day.toString());
            throw new AllocationNothingToAllocateException(day);
        }
        AllocationApprovalPolicy.Decision decision = approvalService.evaluate(organizationId, userId, day,
                new ArrayList<>(usageIds), splits, pending.stream().map(AiUsageAllocation::getId).toList());

        Instant now = Instant.now();
        for (AiUsageAllocation a : pending) {
            if (a.isAgent()) {
                a.setActive(false);
                a.setUpdatedAt(now);
                allocationRepository.save(a);
            } else {
                allocationRepository.deleteById(a.getId());
            }
        }

        AiUsageAllocation allocation = new AiUsageAllocation(
                null, organizationId, userId, day,
                new ArrayList<>(usageIds), splits,
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.MANUAL,
                true, null, now, now);
        decision.applyTo(allocation, now);
        // A correction of the agent's rows does not close the day, like the one made by moving rows.
        allocation.setMovedFromAgent(!agentRowIds.isEmpty());

        AiUsageAllocation saved = allocationRepository.save(allocation);
        allocationRepository.updateAllocationIdOnUsageRecords(new ArrayList<>(usageIds), saved.getId());
        stampManual(organizationId, agentRowIds, splits, items);
        // Ratios count validated allocations only: an allocation awaiting approval waits for its approval.
        if (allocation.getStatus() == AiUsageAllocation.Status.VALIDATED) {
            items.values().stream().map(WorkItem::getProjectId).filter(Objects::nonNull).distinct()
                    .forEach(projectId -> calculateAiRatiosUseCase.execute(organizationId, projectId));
        }
        return new Result(saved.getId(), allocation.getStatus(), allocation.getApproval());
    }

    /**
     * Everything not yet confirmed on the day: the person's pending and awaiting-approval allocations (agent's
     * and automatic) and the rows with no allocation. {@code VALIDATED} allocations stay out.
     */
    static DayRows collectDayRows(AiUsageAllocationRepository allocationRepository, AIUsageRepository aiUsageRepository,
                                  String organizationId, String userId, LocalDate day) {
        List<AiUsageAllocation> pending = allocationRepository.findByOrgUserAndDay(organizationId, userId, day).stream()
                .filter(AiUsageAllocation::isActive)
                .filter(a -> a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION
                        || a.getStatus() == AiUsageAllocation.Status.AWAITING_APPROVAL)
                .filter(a -> userId.equals(a.getUserId()))
                .toList();
        Set<String> agentRowIds = new LinkedHashSet<>();
        Set<String> usageIds = new LinkedHashSet<>();
        for (AiUsageAllocation a : pending) {
            if (a.getAiUsageIds() == null) continue;
            usageIds.addAll(a.getAiUsageIds());
            if (a.isAgent()) agentRowIds.addAll(a.getAiUsageIds());
        }
        aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .forEach(u -> usageIds.add(u.getId()));
        return new DayRows(pending, agentRowIds, usageIds);
    }

    /** The manual correction wins: stamps MANUAL on the agent rows, with the item that got the biggest share. */
    private void stampManual(String organizationId, Set<String> agentRowIds, List<AllocationSplit> splits,
                             Map<String, WorkItem> items) {
        if (agentRowIds.isEmpty()) return;
        WorkItem main = splits.stream()
                .filter(s -> s.getWorkItemId() != null && items.containsKey(s.getWorkItemId()))
                .max(Comparator.comparing(AllocationSplit::getPercentage))
                .map(s -> items.get(s.getWorkItemId())).orElse(null);
        agentUsageQueries.stampTask(organizationId, agentRowIds,
                main == null ? null : main.getId(), main == null ? null : main.getProjectId(),
                main == null ? null : AgentReviewReasonCalculator.displayId(main), MANUAL_SOURCE);
    }

    /**
     * Every non-blank {@code workItemId} must be an active work item of the organization, or the
     * whole request is rejected (404) before anything is written. Returns those items by id, whose
     * projects the caller recalculates the AI ratios of.
     */
    private Map<String, WorkItem> requireOrganizationWorkItems(String organizationId, List<AllocationSplit> splits) {
        Map<String, WorkItem> items = new LinkedHashMap<>();
        if (splits == null) return items;
        for (AllocationSplit split : splits) {
            String id = split.getWorkItemId();
            if (id == null || id.isBlank()) continue;
            WorkItem item = workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                    .orElseThrow(() -> new WorkItemNotFoundException(id));
            if (!typeEligibility.isEligible(organizationId, item)) {
                throw new AllocationWorkItemTypeNotEligibleException(item.getRawType());
            }
            items.put(id, item);
        }
        return items;
    }
}

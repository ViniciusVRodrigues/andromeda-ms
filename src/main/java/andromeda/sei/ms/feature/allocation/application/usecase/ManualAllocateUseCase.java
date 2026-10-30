package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.AgentReviewReasonCalculator;
import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNothingToAllocateException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationWorkItemTypeNotEligibleException;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final BigDecimal ONE_SCALED = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    /** Value of {@code attributionSource} stamped on agent rows the person reallocated by hand. */
    static final String MANUAL_SOURCE = "MANUAL";

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final WorkItemRepository workItemRepository;
    private final AllocationTypeEligibility typeEligibility;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;
    private final AgentUsageQueries agentUsageQueries;

    public void execute(String organizationId, String userId, String allocationId,
                        LocalDate day, List<AllocationSplit> splits) {
        validateSplits(splits);

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
            absorbDay(organizationId, userId, allocation.getDay(), splits);
        } else {
            List<AiUsageAllocation> ofTheDay = allocationRepository.findByOrgUserAndDay(organizationId, userId, day);
            if (ofTheDay.stream().anyMatch(a -> a.isActive() && a.closesTheDay())) {
                throw new AllocationAlreadyValidatedException(day.toString());
            }
            absorbDay(organizationId, userId, day, splits);
        }
    }

    private void validateSplits(List<AllocationSplit> splits) {
        BigDecimal sum = splits.stream()
                .map(AllocationSplit::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        if (sum.compareTo(ONE_SCALED) != 0) {
            throw new AllocationSplitsInvalidException();
        }
    }

    /**
     * The person's split replaces everything not yet confirmed on the day (atribuicao-a-prova-de-esquecimento,
     * 1.4): every pending allocation (agent's and automatic) plus the rows with no allocation go to one
     * {@code VALIDATED MANUAL} allocation. The pending ones are dropped (automatic) or deactivated (agent's),
     * and the rows that came from the agent are stamped {@code MANUAL}, so the manual correction wins over a
     * resend. {@code VALIDATED} allocations stay as they are.
     */
    private void absorbDay(String organizationId, String userId, LocalDate day, List<AllocationSplit> splits) {
        List<AiUsageAllocation> pending = allocationRepository.findByOrgUserAndDay(organizationId, userId, day).stream()
                .filter(AiUsageAllocation::isActive)
                .filter(a -> a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION)
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
        Map<String, WorkItem> items = requireOrganizationWorkItems(organizationId, splits);
        if (usageIds.isEmpty()) {
            throw new AllocationNothingToAllocateException(day);
        }

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
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, null, now, now);
        // A correction of the agent's rows does not close the day, like the one made by moving rows.
        allocation.setMovedFromAgent(!agentRowIds.isEmpty());

        AiUsageAllocation saved = allocationRepository.save(allocation);
        allocationRepository.updateAllocationIdOnUsageRecords(new ArrayList<>(usageIds), saved.getId());
        stampManual(organizationId, agentRowIds, splits, items);
        items.values().stream().map(WorkItem::getProjectId).filter(Objects::nonNull).distinct()
                .forEach(projectId -> calculateAiRatiosUseCase.execute(organizationId, projectId));
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

package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAssignAllocationUseCase {

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;

    public void execute(String organizationId, String userId, LocalDate day) {
        // Step 1: Collect all AIUsage record IDs for the day where allocationId == null
        List<AIUsage> unallocated = aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(
                organizationId, userId, day);
        List<String> unallocatedIds = unallocated.stream().map(AIUsage::getId).toList();

        // Step 2: If no unallocated records, return early (idempotent)
        if (unallocatedIds.isEmpty()) {
            log.debug("AutoAssign: no unallocated records for user={} day={} — skipping", userId, day);
            return;
        }

        // Step 3: Check if a PENDING_VALIDATION allocation already covers exactly those record IDs
        List<AiUsageAllocation> existingPending = allocationRepository.findByOrgUserAndDay(
                organizationId, userId, day).stream()
                .filter(a -> a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION)
                .toList();

        if (!existingPending.isEmpty()) {
            AiUsageAllocation existing = existingPending.get(0);
            Set<String> existingIds = existing.getAiUsageIds() == null
                    ? Set.of() : Set.copyOf(existing.getAiUsageIds());
            Set<String> currentIds = Set.copyOf(unallocatedIds);

            if (existingIds.equals(currentIds)) {
                log.debug("AutoAssign: identical PENDING allocation exists for user={} day={} — skipping", userId, day);
                return;
            }

            // Step 4: Stale PENDING exists covering different records — delete it
            log.debug("AutoAssign: stale PENDING allocation for user={} day={} — deleting id={}", userId, day, existing.getId());
            allocationRepository.deleteById(existing.getId());
        }

        // Step 5: Fetch sprint work items assigned to the user for the allocation day
        List<Sprint> currentSprints = new java.util.ArrayList<>(
                sprintRepository.findByOrganizationIdAndDay(organizationId, day));
        if (currentSprints.isEmpty()) {
            log.debug("AutoAssign: no sprint covers day={} — falling back to closest sprint", day);
            sprintRepository.findClosestSprintByOrganizationIdAndDay(organizationId, day)
                    .ifPresent(currentSprints::add);
        }
        if (currentSprints.isEmpty()) {
            log.debug("AutoAssign: no sprints at all for org={} — skipping", organizationId);
            return;
        }

        List<WorkItem> inProgressItems = currentSprints.stream()
                .flatMap(sprint -> workItemRepository
                        .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                                organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS)
                        .stream()
                        .filter(wi -> wi.getResolvedType() == WorkItem.ResolvedType.STORY))
                .toList();

        // Step 6: If no items found, skip — records stay unallocated for manual allocation
        if (inProgressItems.isEmpty()) {
            log.debug("AutoAssign: no in-progress items for user={} day={} — leaving unallocated", userId, day);
            return;
        }

        // Step 7: Create one AiUsageAllocation envelope with even splits
        Instant now = Instant.now();
        BigDecimal evenSplit = BigDecimal.ONE
                .divide(BigDecimal.valueOf(inProgressItems.size()), 4, RoundingMode.HALF_UP);

        List<AllocationSplit> splits = inProgressItems.stream()
                .map(item -> new AllocationSplit(item.getId(), item.getTitle(), evenSplit))
                .toList();

        AiUsageAllocation allocation = new AiUsageAllocation(
                null,
                organizationId,
                userId,
                day,
                unallocatedIds,
                splits,
                AiUsageAllocation.Status.PENDING_VALIDATION,
                AiUsageAllocation.AssignedBy.AUTO,
                true,
                null,
                now,
                now
        );

        AiUsageAllocation saved = allocationRepository.save(allocation);

        log.debug("AutoAssign: allocation created id={} for user={} day={} covering {} records with {} splits",
                saved.getId(), userId, day, unallocatedIds.size(), splits.size());
    }
}

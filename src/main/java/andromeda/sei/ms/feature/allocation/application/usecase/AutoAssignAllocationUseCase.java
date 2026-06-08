package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAssignAllocationUseCase {

    private final AiUsageAllocationRepository allocationRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;

    public void execute(String organizationId, String userId, LocalDate day) {
        if (allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(organizationId, userId, day)) {
            return;
        }

        List<Sprint> currentSprints = sprintRepository.findCurrentSprintsByOrganizationId(organizationId);
        if (currentSprints.isEmpty()) {
            return;
        }

        List<WorkItem> inProgressItems = currentSprints.stream()
                .flatMap(sprint -> workItemRepository
                        .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                                organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS)
                        .stream())
                .toList();

        allocationRepository.deleteByOrganizationIdAndUserIdAndDay(organizationId, userId, day);

        if (inProgressItems.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        BigDecimal evenSplit = BigDecimal.ONE
                .divide(BigDecimal.valueOf(inProgressItems.size()), 4, RoundingMode.HALF_UP);

        List<AiUsageAllocation> allocations = inProgressItems.stream()
                .map(item -> new AiUsageAllocation(
                        null,
                        organizationId,
                        userId,
                        day,
                        item.getId(),
                        item.getTitle(),
                        evenSplit,
                        AiUsageAllocation.Status.PENDING_VALIDATION,
                        AiUsageAllocation.AssignedBy.AUTO,
                        true,
                        null,
                        now,
                        now
                ))
                .toList();

        allocationRepository.saveAll(allocations);
        log.debug("AutoAssign: {} allocations created for user={} day={}", allocations.size(), userId, day);
    }
}

package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryInput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationEmptyException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationInvalidSumException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces the user's allocation of a day with a validated manual one.
 *
 * <p>metrics-correctness 11.3/11.4: the allocation now carries the day's usage record ids — without
 * them its cost basis was always zero, so manual allocations never reached any cost metric — and
 * the usage records point back to it. Records that only a replaced allocation referenced get their
 * {@code allocationId} cleared, instead of pointing at an allocation that no longer exists.
 */
@Service
@RequiredArgsConstructor
public class SaveAllocationUseCase {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");
    private static final int PERCENTAGE_SCALE = 4;

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;

    public void execute(String organizationId, String userId, LocalDate day, List<AllocationEntryInput> entries) {
        validate(entries);

        Instant now = Instant.now();
        List<AllocationSplit> splits = entries.stream()
                .map(e -> new AllocationSplit(e.workItemId(), e.workItemTitle(),
                        e.percentage().setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP)))
                .toList();
        List<String> dayUsageIds = aiUsageRepository
                .findByOrganizationIdAndUserIdAndDayIn(organizationId, userId, List.of(day)).stream()
                .map(AIUsage::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<String> previouslyReferenced = allocationRepository.findByOrgUserAndDay(organizationId, userId, day).stream()
                .filter(a -> a.getAiUsageIds() != null)
                .flatMap(a -> a.getAiUsageIds().stream())
                .collect(Collectors.toSet());

        AiUsageAllocation allocation = new AiUsageAllocation(null, organizationId, userId, day, dayUsageIds, splits,
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, null, now, now);

        allocationRepository.deleteByOrganizationIdAndUserIdAndDay(organizationId, userId, day);
        AiUsageAllocation saved = allocationRepository.save(allocation);

        allocationRepository.updateAllocationIdOnUsageRecords(dayUsageIds, saved.getId());
        List<String> orphaned = previouslyReferenced.stream().filter(id -> !dayUsageIds.contains(id)).toList();
        allocationRepository.updateAllocationIdOnUsageRecords(orphaned, null);
    }

    private void validate(List<AllocationEntryInput> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new AllocationEmptyException();
        }
        BigDecimal sum = entries.stream()
                .map(AllocationEntryInput::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (BigDecimal.ONE.subtract(sum).abs().compareTo(TOLERANCE) > 0) {
            throw new AllocationInvalidSumException();
        }
    }
}

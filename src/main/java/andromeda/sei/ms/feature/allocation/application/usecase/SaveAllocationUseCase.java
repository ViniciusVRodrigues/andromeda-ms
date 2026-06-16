package andromeda.sei.ms.feature.allocation.application.usecase;

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

@Service
@RequiredArgsConstructor
public class SaveAllocationUseCase {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    private final AiUsageAllocationRepository allocationRepository;

    public void execute(String organizationId, String userId, LocalDate day, List<AllocationEntryInput> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new AllocationEmptyException();
        }

        BigDecimal sum = entries.stream()
                .map(AllocationEntryInput::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (BigDecimal.ONE.subtract(sum).abs().compareTo(TOLERANCE) > 0) {
            throw new AllocationInvalidSumException();
        }

        Instant now = Instant.now();
        List<AllocationSplit> splits = entries.stream()
                .map(e -> new AllocationSplit(
                        e.workItemId(),
                        e.workItemTitle(),
                        e.percentage().setScale(4, RoundingMode.HALF_UP)
                ))
                .toList();

        AiUsageAllocation allocation = new AiUsageAllocation(
                null,
                organizationId,
                userId,
                day,
                List.of(),
                splits,
                AiUsageAllocation.Status.VALIDATED,
                AiUsageAllocation.AssignedBy.MANUAL,
                true,
                null,
                now,
                now
        );

        allocationRepository.deleteByOrganizationIdAndUserIdAndDay(organizationId, userId, day);
        allocationRepository.save(allocation);
    }
}

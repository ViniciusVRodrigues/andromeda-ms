package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryInput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationEmptyException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationInvalidSumException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
        List<AiUsageAllocation> allocations = entries.stream()
                .map(e -> new AiUsageAllocation(
                        null,
                        organizationId,
                        userId,
                        day,
                        e.workItemId(),
                        e.workItemTitle(),
                        e.percentage().setScale(4, java.math.RoundingMode.HALF_UP),
                        AiUsageAllocation.Status.VALIDATED,
                        AiUsageAllocation.AssignedBy.MANUAL,
                        true,
                        null,
                        now,
                        now
                ))
                .toList();

        allocationRepository.deleteByOrganizationIdAndUserIdAndDay(organizationId, userId, day);
        allocationRepository.saveAll(allocations);
    }
}

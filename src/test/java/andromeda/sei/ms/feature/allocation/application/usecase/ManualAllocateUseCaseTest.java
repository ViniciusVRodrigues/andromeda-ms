package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualAllocateUseCaseTest {

    @Mock
    private AiUsageAllocationRepository allocationRepository;

    @Mock
    private AIUsageRepository aiUsageRepository;

    @InjectMocks
    private ManualAllocateUseCase useCase;

    private static final String ORG_ID = "org-1";
    private static final String USER_ID = "user-1";
    private static final String ALLOCATION_ID = "alloc-1";
    private static final LocalDate DAY = LocalDate.of(2025, 6, 1);

    private List<AllocationSplit> validSplits;
    private AiUsageAllocation pendingAllocation;

    @BeforeEach
    void setUp() {
        validSplits = List.of(
                new AllocationSplit("wi-1", "Feature A", new BigDecimal("0.6000")),
                new AllocationSplit("wi-2", "Feature B", new BigDecimal("0.4000")));

        Instant now = Instant.now();
        pendingAllocation = new AiUsageAllocation(
                ALLOCATION_ID, ORG_ID, USER_ID, DAY,
                List.of("usage-1"), List.of(),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, 0L, now, now);
    }

    @Test
    void shouldAllocateManually_whenAllocationIdProvided() {
        when(allocationRepository.findById(ALLOCATION_ID, ORG_ID))
                .thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.save(any())).thenReturn(pendingAllocation);

        useCase.execute(ORG_ID, USER_ID, ALLOCATION_ID, null, validSplits);

        verify(allocationRepository).save(pendingAllocation);
        verify(allocationRepository).updateAllocationIdOnUsageRecords(
                eq(List.of("usage-1")), eq(ALLOCATION_ID));
    }

    @Test
    void shouldAllocateManually_whenDayProvided() {
        Instant now = Instant.now();
        AIUsage usage = mockAiUsage("usage-2");
        when(aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(ORG_ID, USER_ID, DAY))
                .thenReturn(List.of(usage));

        AiUsageAllocation savedAllocation = new AiUsageAllocation(
                "new-alloc-id", ORG_ID, USER_ID, DAY,
                List.of("usage-2"), validSplits,
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, 0L, now, now);
        when(allocationRepository.save(any())).thenReturn(savedAllocation);

        useCase.execute(ORG_ID, USER_ID, null, DAY, validSplits);

        ArgumentCaptor<AiUsageAllocation> captor = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocationRepository).save(captor.capture());
        AiUsageAllocation saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(saved.getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.MANUAL);
        assertThat(saved.getAiUsageIds()).containsExactly("usage-2");

        verify(allocationRepository).updateAllocationIdOnUsageRecords(
                eq(List.of("usage-2")), eq("new-alloc-id"));
    }

    @Test
    void shouldThrowSplitsInvalid_whenPercentagesSumIsNot1() {
        List<AllocationSplit> invalidSplits = List.of(
                new AllocationSplit("wi-1", "Feature A", new BigDecimal("0.5000")),
                new AllocationSplit("wi-2", "Feature B", new BigDecimal("0.3000")));

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID, null, DAY, invalidSplits))
                .isInstanceOf(AllocationSplitsInvalidException.class);

        verify(allocationRepository, never()).save(any());
        verify(aiUsageRepository, never()).findUnallocatedByOrganizationIdAndUserIdAndDay(any(), any(), any());
    }

    private AIUsage mockAiUsage(String id) {
        Instant now = Instant.now();
        return new AIUsage(id, ORG_ID, USER_ID, null, "ds-1", DAY,
                "gpt-4", "ws-1", "ws-name", "m-1", "m-name",
                1000L, 500L, 5, "vscode", null, BigDecimal.ZERO,
                now, true, 0L, now, now);
    }
}
